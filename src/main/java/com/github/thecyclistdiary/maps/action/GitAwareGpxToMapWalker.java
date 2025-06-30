package com.github.thecyclistdiary.maps.action;

import files.DefaultGpxRunner;
import files.FileRunner;
import files.MarkdownRunner;
import folder.GpxToMapWalker;
import map.gpx.DefaultGpxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

public class GitAwareGpxToMapWalker extends GpxToMapWalker<DefaultGpxRunner, FileRunner> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitAwareGpxToMapWalker.class);

    private final Set<String> modifiedGpxFiles;

    public GitAwareGpxToMapWalker(Set<String> modifiedGpxFiles, DefaultGpxMapper gpxMapper) {
        super(null, new DefaultGpxRunner(gpxMapper), new MarkdownRunner(), true);
        this.modifiedGpxFiles = modifiedGpxFiles;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
        if (modifiedGpxFiles.contains(path.getFileName().toString())) {
            LOGGER.info("The GPX file {} is new or as been modified during last commit, (re-)generating image...", path);
            return super.visitFile(path, attrs);
        }
        return FileVisitResult.CONTINUE;
    }
}
