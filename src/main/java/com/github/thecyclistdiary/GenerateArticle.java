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
import java.nio.file.Files;
import java.nio.file.Path;
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
                        Path path = matchingAdventure.get();
                        Path destinationPath = adventureFolder.resolve(path).resolve(String.format("%s.md", article.title()));
                        try {
                            Files.writeString(destinationPath, article.toString());
                        } catch (IOException e) {
                            System.err.println(e.getMessage());
                            commands.error("Le nouvel article n'a pas pu être écrit à cause d'une erreur d'I/O");
                            System.exit(ERROR_STATUS);
                        }
                        try {
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
                            issue.close(GHIssueStateReason.COMPLETED);
                        } catch (GitAPIException e) {
                            System.err.println(e.getMessage());
                            commands.error("Le nouvel article n'a pas pu être commité");
                            System.exit(ERROR_STATUS);
                        } catch (IOException e) {
                            System.err.println(e.getMessage());
                            commands.error("Impossible de mettre à jour l'issue");
                            System.exit(ERROR_STATUS);
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
}