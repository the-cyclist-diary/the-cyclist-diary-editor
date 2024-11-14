package com.github.thecyclistdiary;

import com.github.thecyclistdiary.article.dto.Article;
import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import io.quarkiverse.githubapp.event.Issue;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHLabel;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

public class GenerateArticle {

    private static final String ARTICLE = "article";
    private static final int ERROR_STATUS = 1;

    @Action
    void action(Commands commands, Context context, Inputs inputs, @Issue GHEventPayload.Issue issuePayload) throws IOException, GitAPIException {
        commands.echo("Traitement de l'issue...");
        GHIssue issue = issuePayload.getIssue();
        if (issue.getLabels().stream().map(GHLabel::getName).anyMatch(ARTICLE::equals)) {
            commands.echo("L'issue est bien un article, parsing...");
            String token = inputs.getRequired("github-token");
            String username = inputs.getRequired("github-username");
            Article article = ArticleParser.parse(issue);
            Path repoDirectory = Files.createTempDirectory("git");
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                    .setDirectory(repoDirectory.toFile())
                    .setBranch(context.getGitHubRef())
                    .setURI(String.format("%s/%s", context.getGitHubServerUrl(), context.getGitHubRepository()));
            try (Git git = cloneCommand.call()) {
                Path adventureFolder = repoDirectory.resolve("content").resolve("adventures");
                commands.echo(String.format("Recherche de l'aventure %s", article.folder()));
                try (Stream<Path> files = Files.list(adventureFolder)) {
                    Optional<Path> matchingAdventure = files.filter(path -> article.folder().equalsIgnoreCase(path.getFileName().toString()))
                            .findAny();
                    if (matchingAdventure.isPresent()) {
                        Path adventurePath = matchingAdventure.get();
                        Path articlePath = adventureFolder.resolve(adventurePath).resolve(article.title());
                        Files.createDirectory(articlePath);
                        try {
                            Path articleFile = articlePath.resolve(String.format("%s.md", article.title()));
                            Files.writeString(articleFile, article.toString());
                            if (article.gpxUrl() != null) {
                                downloadGpxFile(commands, article, articlePath);
                            }
                        } catch (IOException e) {
                            logErrorAndExit(e.getMessage(), commands,
                                    "Le nouvel article n'a pas pu être écrit à cause d'une erreur d'I/O");
                        } catch (URISyntaxException e) {
                            logErrorAndExit(e.getMessage(), commands,
                                    "Impossible de télécharger le fichier gpx lié à l'article");
                        }
                        try {
                            commitAndPush(commands, git, article, username, token, issue);
                            issue.close(GHIssueStateReason.COMPLETED);
                        } catch (GitAPIException e) {
                            logErrorAndExit(e.getMessage(), commands, "Le nouvel article n'a pas pu être commité");
                        } catch (IOException e) {
                            logErrorAndExit(e.getMessage(), commands, "Impossible de mettre à jour l'issue");
                        }
                    } else {
                        commands.error("L'aventure n'a pas pu être trouvée");
                        System.exit(ERROR_STATUS);
                    }
                }
            }
        }
        commands.appendJobSummary(String.format(":wave: L'article %s a bien été généré !", issue.getTitle()));
    }

    private static void downloadGpxFile(Commands commands, Article article, Path articlePath) throws IOException, URISyntaxException {
        InputStream in = new URI(article.gpxUrl()).toURL().openStream();
        String gpxName = String.format("%s.gpx", article.title());
        Files.copy(in, articlePath.resolve(gpxName),
                StandardCopyOption.REPLACE_EXISTING);
        commands.echo(String.format("Le fichier %s a bien été téléchargé", gpxName));
    }

    private static void logErrorAndExit(String e, Commands commands, String message) {
        System.err.println(e);
        commands.error(message);
        System.exit(ERROR_STATUS);
    }

    private static void commitAndPush(Commands commands, Git git, Article article, String username, String token, GHIssue issue) throws GitAPIException, IOException {
        git.add().addFilepattern(".").call();
        commands.echo("Modifications indexed");
        String commitMessage = String.format("feat: nouvel article - %s", article.title());
        git.commit()
                .setMessage(commitMessage)
                .setAuthor("Ivan Béthus", "ivan.bethus@gmail.com")
                .call();
        commands.echo("Modifications committed");
        git.push()
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                .call();
        commands.echo(String.format("Modifications pushed - Commit message : %s", commitMessage));
        issue.comment(String.format("Article généré le %s dans content/adventures/%s/%s.md", LocalDate.now(),
                article.folder(),
                article.title()));
    }
}