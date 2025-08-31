package com.github.thecyclistdiary.maps.action;

import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.logging.Log;
import map.gpx.DefaultGpxMapper;
import map.gpx.GpxStyler;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHEventPayload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;

public class MapsCreation {
    @Action("Generate maps")
    void generateMaps(Inputs inputs, Context context,
                      @PullRequest GHEventPayload.PullRequest pullRequestPayload, Commands commands)
            throws IOException, GitAPIException {
        String executionFolder = inputs.getRequired("content-path");
        String token = inputs.getRequired("github-token");
        String username = inputs.getRequired("github-username");
        Path repoDirectory = Files.createTempDirectory("git");

        CloneCommand cloneCommand = Git.cloneRepository()
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                .setDirectory(repoDirectory.toFile())
                .setBranch(pullRequestPayload.getPullRequest().getHead().getRef())
                .setURI(String.format("%s/%s", context.getGitHubServerUrl(), context.getGitHubRepository()));
        try (Git git = cloneCommand.call()) {
            Repository repository = git.getRepository();
            Set<String> modifiedGpxFiles = GitHelper.getModifiedGpxList(git, repository);
            GpxStyler gpxStyler = new GpxStyler.builder()
                    .withGraphChartPadding(10)
                    .build();
            DefaultGpxMapper gpxMapper = new DefaultGpxMapper.builder()
                    .withHeight(800)
                    .withWidth(1200)
                    .withChartHeight(100)
                    .withGpxStyler(gpxStyler)
                    .build();
            var gpxToMapWalker = new GitAwareGpxToMapWalker(modifiedGpxFiles, gpxMapper);
            Path completeExecutionFolder = repoDirectory.resolve(executionFolder);
            Log.info("Starting analysis of content folder %s".formatted(completeExecutionFolder));
            Files.walkFileTree(completeExecutionFolder, gpxToMapWalker);
            Log.info("Done analysis of content folder");
            if (gpxToMapWalker.getExtractedResults().findAny().isPresent()) {
                Log.info("Committing to git repository...");
                commitChanges(git, username, token);
                Log.info("Changes committed successfully");
                commands.notice("Changes committed and pushed to the repository.");
            } else {
                commands.warning("No changes to commit, skipping git commit and push.");
            }
        }
    }

    public static void commitChanges(Git gitInstance, String username, String githubToken) {
        try {
            gitInstance.add().addFilepattern(".").call();
            Log.info("Changes indexed");
            String commitMessage = String.format("feat: auto-generated map images - %s", LocalDateTime.now());
            gitInstance.commit()
                    .setMessage(commitMessage)
                    .setAuthor("Ivan Béthus", "ivan.bethus@gmail.com")
                    .call();
            Log.info("Changes committed");
            gitInstance.push()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, githubToken))
                    .call();
            Log.info("Changes pushed - Commit message : %s".formatted(commitMessage));
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }
}
