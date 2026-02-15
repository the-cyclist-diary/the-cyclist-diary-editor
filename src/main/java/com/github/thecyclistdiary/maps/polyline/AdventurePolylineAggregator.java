package com.github.thecyclistdiary.maps.polyline;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Point;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Aggregates multiple GPX files from an adventure into a single combined polyline.
 * Used for multi-day adventures where each stage has its own GPX file.
 */
public class AdventurePolylineAggregator {
    private final GpxPolylineService polylineService;

    public AdventurePolylineAggregator(GpxPolylineService polylineService) {
        this.polylineService = polylineService;
    }

    /**
     * Finds all GPX files in subdirectories and combines them into one polyline.
     * 
     * @param adventureDir The root directory of the adventure (e.g., "Un été outre-Manche")
     * @return EncodedPolyline representing the complete adventure track
     */
    public EncodedPolyline aggregateAdventure(Path adventureDir) throws IOException {
        List<Path> gpxFiles = findAllGpxFiles(adventureDir);
        
        if (gpxFiles.isEmpty()) {
            throw new IOException("No GPX files found in adventure directory: " + adventureDir);
        }

        // Sort GPX files by their start date (first timestamp)
        gpxFiles.sort(Comparator.comparing(
            this::getFirstTimestamp,
            Comparator.nullsLast(Comparator.naturalOrder())
        ));

        Log.info("Found %d GPX files to aggregate for adventure: %s"
            .formatted(gpxFiles.size(), adventureDir.getFileName()));

        // Combine all GPX files
        List<GpxPoint> allPoints = new ArrayList<>();

        for (Path gpxFile : gpxFiles) {
            try {
                GPX gpx = GPX.read(gpxFile);
                List<GpxPoint> points = extractPointsFromGpx(gpx);
                
                if (!points.isEmpty()) {
                    allPoints.addAll(points);
                    Log.debug("  + %s: %d points".formatted(gpxFile.getFileName(), points.size()));
                }
            } catch (Exception e) {
                Log.warn("Failed to read GPX file %s, skipping: %s"
                    .formatted(gpxFile.getFileName(), e.getMessage()));
            }
        }

        if (allPoints.isEmpty()) {
            throw new IOException("No valid points found in any GPX file");
        }

        Log.info("Combined %d total points from %d GPX files"
            .formatted(allPoints.size(), gpxFiles.size()));

        // Encode the combined track
        return polylineService.encode(allPoints);
    }

    /**
     * Recursively finds all GPX files under the adventure directory.
     */
    private List<Path> findAllGpxFiles(Path dir) throws IOException {
        List<Path> gpxFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().toLowerCase().endsWith(".gpx"))
                  .forEach(gpxFiles::add);
        }
        return gpxFiles;
    }

    /**
     * Gets the first timestamp from a GPX file to determine chronological order.
     * Returns null if no timestamp is found (these files will be sorted last).
     */
    private ZonedDateTime getFirstTimestamp(Path gpxFile) {
        try {
            GPX gpx = GPX.read(gpxFile);
            
            for (Track track : gpx.getTracks()) {
                for (TrackSegment segment : track.getSegments()) {
                    for (Point point : segment.getPoints()) {
                        if (point.getTime().isPresent()) {
                            return point.getTime().get().atZone(ZoneId.of("UTC"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.warn("Could not read timestamp from %s: %s"
                .formatted(gpxFile.getFileName(), e.getMessage()));
        }
        
        return null;
    }

    /**
     * Extracts points from a GPX object.
     */
    private List<GpxPoint> extractPointsFromGpx(GPX gpx) {
        List<GpxPoint> points = new ArrayList<>();
        
        for (Track track : gpx.getTracks()) {
            for (TrackSegment segment : track.getSegments()) {
                for (Point point : segment.getPoints()) {
                    double lat = point.getLatitude().doubleValue();
                    double lon = point.getLongitude().doubleValue();
                    double elevation = point.getElevation()
                        .map(e -> e.doubleValue())
                        .orElse(0.0);
                    ZonedDateTime timestamp = point.getTime()
                        .map(instant -> instant.atZone(ZoneId.of("UTC")))
                        .orElse(null);
                    
                    points.add(new GpxPoint(lat, lon, elevation, timestamp));
                }
            }
        }
        
        return points;
    }

    /**
     * Checks if a directory looks like an adventure directory
     * (contains subdirectories with GPX files).
     */
    public static boolean isAdventureDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }

        // Check if there are subdirectories with GPX files
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                        .anyMatch(subDir -> {
                            try (Stream<Path> subStream = Files.list(subDir)) {
                                return subStream.anyMatch(p -> 
                                    Files.isRegularFile(p) && 
                                    p.toString().toLowerCase().endsWith(".gpx")
                                );
                            } catch (IOException e) {
                                return false;
                            }
                        });
        }
    }
}
