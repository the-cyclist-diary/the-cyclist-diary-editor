package com.github.thecyclistdiary;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class GitService {

    public static void cloneRepository(){

    }

    public static void commitChanges(Git gitInstance, String username, String githubToken) {
        try {
            gitInstance.add().addFilepattern(".").call();
            String commitMessage = String.format("deploy: generated article - %s", LocalDateTime.now());
            gitInstance.commit()
                    .setMessage(commitMessage)
                    .setAuthor("Article generator Bot", "ivan.bethus@gmail.com")
                    .call();
            gitInstance.push()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, githubToken))
                    .call();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }
}
