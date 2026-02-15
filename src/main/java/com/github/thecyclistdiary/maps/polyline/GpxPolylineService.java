package com.github.thecyclistdiary.maps.polyline;

import io.jenetics.jpx.GPX;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Service principal pour parser des fichiers GPX et générer des polylines encodées.
 */
public class GpxPolylineService {
    
    private static final String PATH_ENCODING_FORMAT = "base64-polyline";
    private static final String ELEVATION_ENCODING_FORMAT = "base64-int16-decimeters";
    private static final double EARTH_RADIUS_KM = 6371.0;
    
    /**
     * Parse un fichier GPX et extrait tous les track points.
     * 
     * @param gpxFile Chemin vers le fichier GPX
     * @return Liste des points GPX avec coordonnées et altitude
     * @throws IOException Si le fichier ne peut pas être lu
     * @throws IllegalArgumentException Si le fichier ne contient aucun track point
     */
    public List<GpxPoint> parseGpx(Path gpxFile) throws IOException {
        GPX gpx = GPX.Reader.DEFAULT.read(gpxFile);
        List<GpxPoint> points = new ArrayList<>();
    
        gpx.tracks().forEach(track -> {
            track.segments().forEach(segment -> {
                segment.points().forEach(wayPoint -> {    
                    double lat = wayPoint.getLatitude().doubleValue();
                    double lon = wayPoint.getLongitude().doubleValue();
                    double elevation = wayPoint.getElevation()
                            .map(e -> e.doubleValue())
                            .orElse(0.0);
                    ZonedDateTime time = wayPoint.getTime()
                            .map(instant -> instant.atZone(ZoneId.of("UTC")))
                            .orElse(null);
                    
                    points.add(new GpxPoint(lat, lon, elevation, time));
                });
            });
        });
        
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Le fichier GPX ne contient aucun track point : " + gpxFile);
        }
        
        Log.debug("Parsed %d points from GPX file: %s".formatted(points.size(), gpxFile.getFileName()));
        return points;
    }
    
    /**
     * Calcule les métadonnées d'un tracé GPX.
     */
    public TrackMetadata calculateMetadata(List<GpxPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("La liste de points ne peut pas être vide");
        }
        
        double totalDistance = 0.0;
        double elevationGain = 0.0;
        double elevationLoss = 0.0;
        double minElevation = Double.MAX_VALUE;
        double maxElevation = Double.MIN_VALUE;
        
        ZonedDateTime startTime = points.get(0).time();
        ZonedDateTime endTime = null;
        
        for (int i = 0; i < points.size(); i++) {
            GpxPoint point = points.get(i);
            
            minElevation = Math.min(minElevation, point.elevation());
            maxElevation = Math.max(maxElevation, point.elevation());
            
            if (i > 0) {
                GpxPoint prevPoint = points.get(i - 1);
                totalDistance += calculateDistance(prevPoint, point);
                
                double elevationDiff = point.elevation() - prevPoint.elevation();
                if (elevationDiff > 0) {
                    elevationGain += elevationDiff;
                } else {
                    elevationLoss += Math.abs(elevationDiff);
                }
                
                if (point.time() != null) {
                    endTime = point.time();
                }
            }
        }
        
        Long durationSeconds = null;
        Double averageSpeedKmh = null;
        
        if (startTime != null && endTime != null) {
            durationSeconds = Duration.between(startTime, endTime).getSeconds();
            if (durationSeconds > 0) {
                averageSpeedKmh = (totalDistance / durationSeconds) * 3600;
            }
        }
        
        return new TrackMetadata(
            totalDistance,
            elevationGain,
            elevationLoss,
            minElevation,
            maxElevation,
            durationSeconds,
            startTime,
            endTime,
            averageSpeedKmh
        );
    }
    
    /**
     * Calcule la distance entre deux points avec la formule de Haversine.
     */
    private double calculateDistance(GpxPoint p1, GpxPoint p2) {
        double lat1Rad = Math.toRadians(p1.lat());
        double lat2Rad = Math.toRadians(p2.lat());
        double deltaLat = Math.toRadians(p2.lat() - p1.lat());
        double deltaLon = Math.toRadians(p2.lon() - p1.lon());
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * Encode un fichier GPX en polyline avec altitudes.
     * 
     * @param gpxFile Chemin vers le fichier GPX
     * @return EncodedPolyline contenant la polyline et les altitudes encodées
     * @throws IOException Si le fichier ne peut pas être lu
     */
    public EncodedPolyline encode(Path gpxFile) throws IOException {
        List<GpxPoint> points = parseGpx(gpxFile);
        
        // Encoder la polyline standard Google puis la convertir en Base64 pour éviter les caractères problématiques en JSON
        String polyline = PolylineEncoder.encode(points);
        String encodedPath = Base64.getEncoder().encodeToString(polyline.getBytes());
        
        String encodedElevations = ElevationEncoder.encode(points);
        TrackMetadata metadata = calculateMetadata(points);
        
        return new EncodedPolyline(
                encodedPath,
                PATH_ENCODING_FORMAT,
                encodedElevations,
                ELEVATION_ENCODING_FORMAT,
                points.size(),
                metadata
        );
    }
    
    /**
     * Sauvegarde une polyline encodée au format JSON.
     * 
     * @param encoded Polyline encodée
     * @param outputPath Chemin de sortie
     * @throws IOException Si le fichier ne peut pas être écrit
     */
    public void saveAsJson(EncodedPolyline encoded, Path outputPath) throws IOException {
        String json = encoded.toJson();
        Files.writeString(outputPath, json);
        Log.debug("Saved polyline JSON to: %s".formatted(outputPath));
    }
}
