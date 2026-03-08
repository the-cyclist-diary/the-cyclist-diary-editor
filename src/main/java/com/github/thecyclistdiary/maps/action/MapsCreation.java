package com.github.thecyclistdiary.maps.action;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;

import com.github.thecyclistdiary.maps.polyline.GpxPolylineService;

import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkus.logging.Log;

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

        GHPullRequest pr = pullRequestPayload.getPullRequest();
        CloneCommand cloneCommand = Git.cloneRepository()
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                .setDirectory(repoDirectory.toFile())
                .setBranch(pr.getHead().getRef())
                .setURI(String.format("%s/%s", context.getGitHubServerUrl(), context.getGitHubRepository()))
                .setDepth(2)
                .setCloneAllBranches(false);
        try (Git git = cloneCommand.call()) {
            Repository repository = git.getRepository();

            // Fetch et merge la branche de base pour s'assurer qu'on est à jour
            String baseBranch = pr.getBase().getRef();
            Log.info("Fetching base branch %s...".formatted(baseBranch));
            git.fetch()
                    .setRemote("origin")
                    .setRefSpecs(String.format("+refs/heads/%s:refs/remotes/origin/%s", baseBranch, baseBranch))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                    .call();

            Log.info("Merging base branch %s into current branch...".formatted(baseBranch));
            git.merge()
                    .include(repository.resolve(String.format("origin/%s", baseBranch)))
                    .call();
            Log.info("Base branch merged successfully");

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

                Log.info("Merging PR #%d...".formatted(pr.getNumber()));
                // After a push, GitHub needs time to recompute mergeability; poll until known
                Boolean mergeable = null;
                for (int attempt = 0; attempt < 10 && !Boolean.TRUE.equals(mergeable); attempt++) {
                    if (attempt > 0) {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted while waiting for PR mergeability", e);
                        }
                    }
                    pr.refresh();
                    mergeable = pr.getMergeable();
                    Log.info("PR #%d mergeable state: %s (attempt %d)".formatted(pr.getNumber(), mergeable, attempt + 1));
                }
                if (!Boolean.TRUE.equals(mergeable)) {
                    throw new IOException("PR #%d is not mergeable after waiting (state: %s)".formatted(pr.getNumber(), mergeable));
                }
                
                // Wait a bit more after mergeability is confirmed to ensure GitHub is ready
                Log.info("PR is mergeable, waiting 10 seconds before merge to ensure GitHub is ready...");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting before merge", e);
                }
                
                pr.merge("Maps generated successfully", pr.getHead().getSha(), GHPullRequest.MergeMethod.SQUASH);
                commands.notice("Pull Request #%d merged successfully!".formatted(pr.getNumber()));
                Log.info("PR #%d merged successfully".formatted(pr.getNumber()));

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
