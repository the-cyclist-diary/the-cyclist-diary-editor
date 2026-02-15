package com.github.thecyclistdiary.maps.polyline;

/**
 * Représente une polyline encodée avec ses altitudes encodées séparément.
 * 
 * @param path Polyline encodée au format Google (latitude/longitude)
 * @param elevations Altitudes encodées en Base64 (Int16Array en décimètres)
 * @param elevationEncoding Format d'encodage des altitudes (toujours "base64-int16-decimeters")
 * @param pointCount Nombre de points dans la polyline
 * @param metadata Métadonnées calculées du tracé (distance, dénivelé, etc.)
 */
public record EncodedPolyline(
    String path, 
    String elevations, 
    String elevationEncoding, 
    int pointCount,
    TrackMetadata metadata
) {
    
    /**
     * Convertit l'EncodedPolyline en format JSON simple.
     */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"path\": \"%s\",\n".formatted(path));
        json.append("  \"elevations\": \"%s\",\n".formatted(elevations));
        json.append("  \"elevationEncoding\": \"%s\",\n".formatted(elevationEncoding));
        json.append("  \"pointCount\": %d,\n".formatted(pointCount));
        json.append("  \"metadata\": {\n");
        json.append("    \"distanceKm\": %.3f,\n".formatted(metadata.distanceKm()));
        json.append("    \"elevationGainM\": %.1f,\n".formatted(metadata.elevationGainM()));
        json.append("    \"elevationLossM\": %.1f,\n".formatted(metadata.elevationLossM()));
        json.append("    \"minElevationM\": %.1f,\n".formatted(metadata.minElevationM()));
        json.append("    \"maxElevationM\": %.1f".formatted(metadata.maxElevationM()));
        
        if (metadata.hasTimeData()) {
            json.append(",\n");
            json.append("    \"durationSeconds\": %d,\n".formatted(metadata.durationSeconds()));
            json.append("    \"durationFormatted\": \"%s\",\n".formatted(metadata.formatDuration()));
            json.append("    \"startTime\": \"%s\",\n".formatted(metadata.startTime()));
            json.append("    \"endTime\": \"%s\",\n".formatted(metadata.endTime()));
            json.append("    \"averageSpeedKmh\": %.2f\n".formatted(metadata.averageSpeedKmh()));
        } else {
            json.append("\n");
        }
        
        json.append("  }\n");
        json.append("}");
        return json.toString();
    }
}
