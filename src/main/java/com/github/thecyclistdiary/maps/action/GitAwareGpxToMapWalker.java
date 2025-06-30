package com.github.thecyclistdiary.maps.action;

import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import files.DefaultGpxRunner;
import files.FileRunner;
import files.MarkdownRunner;
import folder.GpxToMapWalker;
import io.quarkus.logging.Log;
import map.gpx.DefaultGpxMapper;

public class GitAwareGpxToMapWalker extends GpxToMapWalker<DefaultGpxRunner, FileRunner> {
    private final Set<String> modifiedGpxFiles;

    public GitAwareGpxToMapWalker(Set<String> modifiedGpxFiles, DefaultGpxMapper gpxMapper) {
        super(null, new DefaultGpxRunner(gpxMapper), new MarkdownRunner(), true);
        this.modifiedGpxFiles = modifiedGpxFiles;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
        if (modifiedGpxFiles.contains(path.getFileName().toString())) {
            Log.info("The GPX file %s is new or as been modified during last commit, (re-)generating image..."
                    .formatted(path));
            return super.visitFile(path, attrs);
        }
        return FileVisitResult.CONTINUE;
    }
}
