package com.github.thecyclistdiary.maps.polyline;

import io.jenetics.jpx.GPX;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service principal pour parser des fichiers GPX et générer des polylines encodées.
 */
public class GpxPolylineService {
    
    private static final String ELEVATION_ENCODING_FORMAT = "base64-int16-decimeters";
    
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
                    
                    points.add(new GpxPoint(lat, lon, elevation));
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
     * Encode un fichier GPX en polyline avec altitudes.
     * 
     * @param gpxFile Chemin vers le fichier GPX
     * @return EncodedPolyline contenant la polyline et les altitudes encodées
     * @throws IOException Si le fichier ne peut pas être lu
     */
    public EncodedPolyline encode(Path gpxFile) throws IOException {
        List<GpxPoint> points = parseGpx(gpxFile);
        
        String encodedPath = PolylineEncoder.encode(points);
        String encodedElevations = ElevationEncoder.encode(points);
        
        return new EncodedPolyline(
                encodedPath,
                encodedElevations,
                ELEVATION_ENCODING_FORMAT,
                points.size()
        );
    }
    
    /**
     * Encode un fichier GPX et sauvegarde le résultat en JSON.
     * 
     * @param gpxFile Chemin vers le fichier GPX
     * @param outputPath Chemin de sortie pour le fichier JSON
     * @throws IOException Si le fichier ne peut pas être lu ou écrit
     */
    public void encodeAndSave(Path gpxFile, Path outputPath) throws IOException {
        EncodedPolyline encoded = encode(gpxFile);
        saveAsJson(encoded, outputPath);
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
