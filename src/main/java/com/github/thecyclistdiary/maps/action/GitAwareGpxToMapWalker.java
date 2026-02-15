package com.github.thecyclistdiary.maps.action;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import com.github.thecyclistdiary.maps.polyline.EncodedPolyline;
import com.github.thecyclistdiary.maps.polyline.GpxPolylineService;

import io.quarkus.logging.Log;

public class GitAwareGpxToMapWalker extends SimpleFileVisitor<Path> {
    private final Set<String> modifiedGpxFiles;
    private final GpxPolylineService polylineService;
    private final boolean fullScanMode;

    public GitAwareGpxToMapWalker(Set<String> modifiedGpxFiles, GpxPolylineService polylineService) {
        this(modifiedGpxFiles, polylineService, false);
    }

    public GitAwareGpxToMapWalker(Set<String> modifiedGpxFiles, GpxPolylineService polylineService, boolean fullScanMode) {
        this.modifiedGpxFiles = modifiedGpxFiles;
        this.polylineService = polylineService;
        this.fullScanMode = fullScanMode;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
        boolean shouldProcess = path.toString().endsWith(".gpx") && 
            (fullScanMode || modifiedGpxFiles.contains(path.getFileName().toString()));
        
        if (shouldProcess) {
            String mode = fullScanMode ? "full scan" : "modified";
            Log.info("GPX file %s (%s mode), generating polyline...".formatted(path.getFileName(), mode));
            
            try {
                EncodedPolyline encoded = polylineService.encode(path);
                
                Path outputPath = path.getParent().resolve(
                    path.getFileName().toString().replace(".gpx", ".polyline.json")
                );
                polylineService.saveAsJson(encoded, outputPath);
                
                Log.info("Generated polyline: %d points, %.2f km, %s"
                    .formatted(
                        encoded.pointCount(),
                        encoded.metadata().distanceKm(),
                        encoded.metadata().formatDuration()
                    ));
            } catch (IOException e) {
                Log.error("Failed to generate polyline for %s: %s"
                    .formatted(path.getFileName(), e.getMessage()), e);
            }
        }
        return FileVisitResult.CONTINUE;
    }
}
