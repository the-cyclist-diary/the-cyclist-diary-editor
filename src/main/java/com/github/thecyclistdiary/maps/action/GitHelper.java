package com.github.thecyclistdiary.maps.action;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

public class GitHelper {
    public static Set<String> getModifiedGpxList(Git gitInstance, Repository repository) {
        Set<String> modifiedGpxFiles = new HashSet<>();
        try {
            try (// Récupère le dernier commit
            RevWalk revWalk = new RevWalk(repository)) {
                RevCommit latestCommit = revWalk.parseCommit(repository.resolve("HEAD"));
                RevCommit parentCommit = latestCommit.getParent(0);

                // Prépare les deux arbres pour la comparaison
                AbstractTreeIterator oldTreeParser = prepareTreeParser(repository, parentCommit.getId().getName());
                AbstractTreeIterator newTreeParser = prepareTreeParser(repository, latestCommit.getId().getName());

                // Compare les commits
                List<DiffEntry> diffs = gitInstance.diff()
                        .setOldTree(oldTreeParser)
                        .setNewTree(newTreeParser)
                        .call();

                // Filtre les fichiers .gpx
                for (DiffEntry diff : diffs) {
                    String fileName = Path.of(diff.getNewPath()).getFileName().toString();
                    if (fileName.endsWith(".gpx")) {
                        modifiedGpxFiles.add(fileName);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return modifiedGpxFiles;
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws Exception {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(objectId));
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (org.eclipse.jgit.lib.ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, commit.getTree().getId());
            }
            walk.dispose();
            return treeParser;

        }
    }
}
