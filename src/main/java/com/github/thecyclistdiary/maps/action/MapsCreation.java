package com.github.thecyclistdiary.maps.action;

import com.github.thecyclistdiary.maps.polyline.GpxPolylineService;
import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.logging.Log;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;

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
        boolean fullScan = Boolean.parseBoolean(inputs.get("full-scan").orElse("false"));
        Path repoDirectory = Files.createTempDirectory("git");

        CloneCommand cloneCommand = Git.cloneRepository()
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                .setDirectory(repoDirectory.toFile())
                .setBranch(pullRequestPayload.getPullRequest().getHead().getRef())
                .setURI(String.format("%s/%s", context.getGitHubServerUrl(), context.getGitHubRepository()))
                .setDepth(2)
                .setCloneAllBranches(false);
        try (Git git = cloneCommand.call()) {
            Repository repository = git.getRepository();
            Set<String> modifiedGpxFiles = fullScan ? Set.of() : GitHelper.getModifiedGpxList(git, repository);
            GpxPolylineService polylineService = new GpxPolylineService();
            var gpxToMapWalker = new GitAwareGpxToMapWalker(modifiedGpxFiles, polylineService, fullScan);
            Path completeExecutionFolder = repoDirectory.resolve(executionFolder);

            if (fullScan) {
                Log.info("Starting FULL SCAN of content folder %s".formatted(completeExecutionFolder));
            } else {
                Log.info("Starting INCREMENTAL analysis of content folder %s".formatted(completeExecutionFolder));
            }
            Files.walkFileTree(completeExecutionFolder, gpxToMapWalker);
            Log.info("Done analysis of content folder");

            if (fullScan || !modifiedGpxFiles.isEmpty()) {
                Log.info("Committing to git repository...");
                commitChanges(git, username, token);
                Log.info("Changes committed successfully");
                commands.notice("Changes committed and pushed to the repository.");
                GHPullRequest pr = pullRequestPayload.getPullRequest();
                try {
                    Log.info("Updating PR #%d branch with base...".formatted(pr.getNumber()));
                    pr.updateBranch();
                    Log.info("PR #%d branch updated successfully".formatted(pr.getNumber()));
                    
                    Log.info("Merging article PR #%d...".formatted(pr.getNumber()));
                    pr.merge("Maps generated successfully", pr.getHead().getSha(), GHPullRequest.MergeMethod.SQUASH);
                    commands.notice("Pull Request #%d merged successfully!".formatted(pr.getNumber()));
                    Log.info("PR #%d merged".formatted(pr.getNumber()));
                } catch (IOException e) {
                    commands.error("Failed to merge PR: " + e.getMessage());
                    Log.error("Failed to merge PR #%d".formatted(pr.getNumber()), e);
                }

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
