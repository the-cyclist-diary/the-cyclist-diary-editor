package com.github.thecyclistdiary.maps.action;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

import com.github.thecyclistdiary.maps.polyline.AdventurePolylineAggregator;
import com.github.thecyclistdiary.maps.polyline.EncodedPolyline;
import com.github.thecyclistdiary.maps.polyline.GpxPolylineService;

import io.quarkus.logging.Log;

public class GitAwareGpxToMapWalker extends SimpleFileVisitor<Path> {
    private final Set<String> modifiedGpxFiles;
    private final GpxPolylineService polylineService;
    private final AdventurePolylineAggregator adventureAggregator;
    private final boolean fullScanMode;
    private final Set<Path> processedAdventures = new HashSet<>();

    public GitAwareGpxToMapWalker(Set<String> modifiedGpxFiles, GpxPolylineService polylineService) {
        this(modifiedGpxFiles, polylineService, false);
    }

    public GitAwareGpxToMapWalker(Set<String> modifiedGpxFiles, GpxPolylineService polylineService, boolean fullScanMode) {
        this.modifiedGpxFiles = modifiedGpxFiles;
        this.polylineService = polylineService;
        this.adventureAggregator = new AdventurePolylineAggregator(polylineService);
        this.fullScanMode = fullScanMode;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        // Check if this is an adventure directory (contains subdirectories with GPX files)
        if (AdventurePolylineAggregator.isAdventureDirectory(dir) && !processedAdventures.contains(dir)) {
            processedAdventures.add(dir);
            
            boolean shouldProcess = fullScanMode || containsModifiedGpxFiles(dir);
            
            if (shouldProcess) {
                String mode = fullScanMode ? "full scan" : "modified";
                Log.info("Adventure directory %s (%s mode), aggregating polyline..."
                    .formatted(dir.getFileName(), mode));
                
                try {
                    EncodedPolyline encoded = adventureAggregator.aggregateAdventure(dir);
                    
                    // Save at the adventure root level
                    String outputFileName = dir.getFileName().toString() + ".polyline.json";
                    Path outputPath = dir.resolve(outputFileName);
                    polylineService.saveAsJson(encoded, outputPath);
                    
                    Log.info("Generated aggregated polyline: %d points, %.2f km, %s"
                        .formatted(
                            encoded.pointCount(),
                            encoded.metadata().distanceKm(),
                            encoded.metadata().formatDuration()
                        ));
                } catch (IOException e) {
                    Log.error("Failed to generate aggregated polyline for %s: %s"
                        .formatted(dir.getFileName(), e.getMessage()), e);
                }
            }
            
            // Continue walking to process individual GPX files if needed
            return FileVisitResult.CONTINUE;
        }
        
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
        boolean shouldProcess = path.toString().endsWith(".gpx") && 
            (fullScanMode || modifiedGpxFiles.contains(path.getFileName().toString()));
        
        if (shouldProcess) {
            // Check if this GPX is part of an adventure (has been aggregated)
            Path parent = path.getParent();
            if (parent != null && parent.getParent() != null) {
                try {
                    if (processedAdventures.contains(parent.getParent())) {
                        // Skip individual processing if already aggregated
                        Log.debug("Skipping individual GPX %s (already aggregated)"
                            .formatted(path.getFileName()));
                        return FileVisitResult.CONTINUE;
                    }
                } catch (Exception e) {
                    // Continue with individual processing
                }
            }
            
            // Process as individual GPX file (single-day rides, etc.)
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
    
    /**
     * Check if an adventure directory contains any modified GPX files.
     */
    private boolean containsModifiedGpxFiles(Path adventureDir) throws IOException {
        return Files.walk(adventureDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().toLowerCase().endsWith(".gpx"))
            .anyMatch(p -> modifiedGpxFiles.contains(p.getFileName().toString()));
    }
}
