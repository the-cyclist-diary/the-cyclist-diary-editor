package com.github.thecyclistdiary.article.action;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import com.github.thecyclistdiary.NoLabelFoundException;
import com.github.thecyclistdiary.article.dto.Article;
import com.github.thecyclistdiary.article.dto.ArticleGallery;

import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkus.logging.Log;

public class GenerateArticle {

    private static final String ARTICLE = "article";
    private static final int ERROR_STATUS = 1;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    @Inject
    @RestClient
    WebpServerClient webpServerClient;

    @Action("Generate article")
    void action(Commands commands, Context context, Inputs inputs, @Issue GHEventPayload.Issue issuePayload,
            GHRepository repository) throws IOException, GitAPIException {
        commands.echo("Traitement de l'issue...");
        GHIssue issue = issuePayload.getIssue();
        if (ARTICLE.equals(issue.getMilestone().getTitle())) {
            commands.echo("L'issue est bien un article, parsing...");
            String token = inputs.getRequired("github-token");
            String username = inputs.getRequired("github-username");
            String webpServerUrl = inputs.getRequired("webp-server-url");
            String webpServerApiKey = inputs.getRequired("webp-server-api-key");
            
            // Configure WebP server REST client URL dynamically
            System.setProperty("quarkus.rest-client.webp-server.url", webpServerUrl);
            commands.echo(String.format("WebP server configured at: %s", webpServerUrl));
            
            Article article = ArticleParser.parse(issue);
            Path repoDirectory = Files.createTempDirectory("git");
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                    .setDirectory(repoDirectory.toFile())
                    .setBranch(context.getGitHubRef())
                    .setURI(String.format("%s/%s", context.getGitHubServerUrl(), context.getGitHubRepository()))
                    .setDepth(1)
                    .setCloneAllBranches(false);
            try (Git git = cloneCommand.call()) {
                String adventureName = issue.getLabels().stream().findFirst()
                        .orElseThrow(NoLabelFoundException::new).getName();
                Path adventureFolder = repoDirectory.resolve("content").resolve("adventures");
                commands.echo(String.format("Recherche de l'aventure %s", adventureName));
                try (Stream<Path> files = Files.list(adventureFolder)) {
                    Optional<Path> matchingAdventure = files
                            .filter(path -> adventureName.equalsIgnoreCase(path.getFileName().toString()))
                            .findAny();
                    if (matchingAdventure.isPresent()) {
                        writeArticle(commands, matchingAdventure.get(), adventureFolder, article, git, username,
                                token, issue, repository, webpServerUrl, webpServerApiKey);
                    } else {
                        commands.error("L'aventure n'a pas pu être trouvée");
                        System.exit(ERROR_STATUS);
                    }
                }
            } catch (NoLabelFoundException e) {
                logErrorAndExit(commands, "L'issue n'a pas de label !", e);
            }
        }
    }

    private void writeArticle(Commands commands, Path adventurePath, Path adventureFolder, Article article,
            Git git, String username, String token, GHIssue issue, GHRepository repository,
            String webpServerUrl, String webpServerApiKey) throws IOException {
        Path articlePath = adventureFolder.resolve(adventurePath).resolve(article.title());
        Files.createDirectory(articlePath);
        parseArticle(commands, article, articlePath, webpServerUrl, webpServerApiKey);
        try {
            commitAndPush(commands, git, article, username, token, issue, articlePath, repository);
            issue.close(GHIssueStateReason.COMPLETED);
        } catch (GitAPIException e) {
            logErrorAndExit(commands, "Le nouvel article n'a pas pu être commité", e);
        } catch (IOException e) {
            logErrorAndExit(commands, "Impossible de mettre à jour l'issue", e);
        }
    }

    private void parseArticle(Commands commands, Article article, Path articlePath,
            String webpServerUrl, String webpServerApiKey) {
        try {
            Path articleFile = articlePath.resolve("index.md");
            Files.writeString(articleFile, article.toString());
            downloadImageFiles(commands, article, articlePath, webpServerUrl, webpServerApiKey);
            if (article.gpxUrl() != null) {
                downloadGpxFile(commands, article, articlePath);
            }
        } catch (IOException e) {
            logErrorAndExit(commands,
                    "Le nouvel article n'a pas pu être écrit à cause d'une erreur d'I/O", e);
        } catch (URISyntaxException e) {
            logErrorAndExit(commands,
                    "Impossible de télécharger le fichier gpx lié à l'article", e);
        }
    }

    private void downloadImageFiles(Commands commands, Article article, Path articlePath,
            String webpServerUrl, String webpServerApiKey) {
        article.articleParts().stream()
                .filter(p -> p instanceof ArticleGallery)
                .flatMap(gallery -> ((ArticleGallery) gallery).images().stream())
                .forEach(image -> {
                    try {
                        // Download original image to repository (fallback)
                        InputStream in = new URI(image.url()).toURL().openStream();
                        Files.copy(in, articlePath.resolve(image.name()),
                                StandardCopyOption.REPLACE_EXISTING);
                        commands.echo(String.format("L'image %s a bien été téléchargée", image.name()));
                        
                        // Upload to WebP server
                        uploadToWebpServer(commands, image.url(), webpServerApiKey);
                    } catch (IOException | URISyntaxException e) {
                        logErrorAndExit(commands,
                                String.format("Impossible de télécharger l'image à l'adresse %s", image.url()),
                                e);
                    }
                });
    }

    private void uploadToWebpServer(Commands commands, String imageUrl, String apiKey) {
        String authHeader = String.format("Bearer %s", apiKey);
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                UploadResponse response = webpServerClient.uploadFromUrl(
                        authHeader, 
                        new UrlUploadRequest(imageUrl));
                
                if (response.alreadyPresent()) {
                    commands.echo(String.format("Image %s already present on WebP server", response.filename()));
                } else {
                    commands.echo(String.format("Image successfully uploaded to WebP server: %s", response.filename()));
                }
                return; // Success, exit retry loop
                
            } catch (Exception e) {
                commands.warning(String.format(
                        "Attempt %d/%d failed to upload image to WebP server: %s", 
                        attempt, MAX_RETRY_ATTEMPTS, e.getMessage()));
                Log.warn(String.format("WebP server upload attempt %d failed for URL: %s", attempt, imageUrl), e);
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    // Exponential backoff
                    long delayMs = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        commands.warning("Retry interrupted, falling back to original image");
                        return;
                    }
                }
            }
        }
        
        // All retries failed, but we continue (fallback to original image)
        commands.warning(String.format(
                "Failed to upload image to WebP server after %d attempts. Using original image as fallback.", 
                MAX_RETRY_ATTEMPTS));
    }

    private void downloadGpxFile(Commands commands, Article article, Path articlePath)
            throws IOException, URISyntaxException {
        InputStream in = new URI(article.gpxUrl()).toURL().openStream();
        String gpxName = String.format("%s.gpx", article.title());
        Files.copy(in, articlePath.resolve(gpxName),
                StandardCopyOption.REPLACE_EXISTING);
        commands.echo(String.format("Le fichier %s a bien été téléchargé", gpxName));
    }

    private void logErrorAndExit(Commands commands, String message, Exception e) {
        commands.error(message);
        Log.error(e.getMessage(), e);
        System.exit(ERROR_STATUS);
    }

    private void commitAndPush(Commands commands, Git git, Article article, String username, String token,
            GHIssue issue,
            Path articlePath, GHRepository repository) throws GitAPIException, IOException {
        String newBranch = UUID.randomUUID().toString();
        git.branchCreate()
                .setName(newBranch)
                .setStartPoint("HEAD")
                .call();
        commands.echo(String.format("Nouvelle branche créée : %s", newBranch));
        git.checkout().setName(newBranch).call();
        commands.echo(String.format("Changement de branche vers : %s", newBranch));
        git.add().addFilepattern(".").call();
        commands.echo("Modifications indexées");
        String commitMessage = String.format("feat: nouvel article - %s", article.title());
        git.commit()
                .setMessage(commitMessage)
                .setAuthor("Ivan Béthus", "ivan.bethus@gmail.com")
                .call();
        commands.echo("Modifications committed");
        git.push()
                .setRemote("origin")
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                .call();
        commands.echo(String.format("Modifications pushed - Commit message : %s", commitMessage));
        GHPullRequest pullRequest = repository.createPullRequest(
                String.format("article/%s", article.title()),
                newBranch,
                "main",
                commitMessage);
        commands.echo(String.format("Pull Request #%d créée : %s", pullRequest.getNumber(), pullRequest.getHtmlUrl()));
        issue.comment(String.format("Article généré le %s dans content/adventures/%s.md\nPull Request: %s", 
                LocalDate.now(), articlePath, pullRequest.getHtmlUrl()));
    }
}