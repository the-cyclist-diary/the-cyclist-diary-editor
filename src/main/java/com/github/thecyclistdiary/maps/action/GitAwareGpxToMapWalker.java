package com.github.thecyclistdiary.maps.action;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import com.github.thecyclistdiary.maps.polyline.EncodedPolyline;
import com.github.thecyclistdiary.maps.polyline.GpxPolylineService;

import files.DefaultGpxRunner;
import files.FileRunner;
import files.MarkdownRunner;
import folder.GpxToMapWalker;
import io.quarkus.logging.Log;
import map.gpx.DefaultGpxMapper;

public class GitAwareGpxToMapWalker extends GpxToMapWalker<DefaultGpxRunner, FileRunner> {
    private final Set<String> modifiedGpxFiles;
    private final GpxPolylineService polylineService;

    public GitAwareGpxToMapWalker(Set<String> modifiedGpxFiles, DefaultGpxMapper gpxMapper, GpxPolylineService polylineService) {
        super(null, new DefaultGpxRunner(gpxMapper), new MarkdownRunner(), true);
        this.modifiedGpxFiles = modifiedGpxFiles;
        this.polylineService = polylineService;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
        if (modifiedGpxFiles.contains(path.getFileName().toString())) {
            Log.info("The GPX file %s is new or as been modified during last commit, (re-)generating image..."
                    .formatted(path));
            
            // Générer la carte PNG (comportement existant)
            FileVisitResult result = super.visitFile(path, attrs);
            
            // Générer la polyline en parallèle
            try {
                EncodedPolyline encoded = polylineService.encode(path);
                Path outputPath = path.getParent().resolve(
                    path.getFileName().toString().replace(".gpx", ".polyline.json")
                );
                polylineService.saveAsJson(encoded, outputPath);
                Log.info("Generated polyline for %s (%d points)".formatted(
                    path.getFileName(), encoded.pointCount()
                ));
            } catch (IOException e) {
                Log.error("Failed to generate polyline for %s: %s".formatted(
                    path.getFileName(), e.getMessage()
                ), e);
            }
            
            return result;
        }
        return FileVisitResult.CONTINUE;
    }
}
