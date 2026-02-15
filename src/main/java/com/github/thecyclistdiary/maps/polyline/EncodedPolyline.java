package com.github.thecyclistdiary.maps.polyline;

/**
 * Représente une polyline encodée avec ses altitudes encodées séparément.
 * 
 * @param path Polyline encodée au format Google (latitude/longitude)
 * @param elevations Altitudes encodées en Base64 (Int16Array en décimètres)
 * @param elevationEncoding Format d'encodage des altitudes (toujours "base64-int16-decimeters")
 * @param pointCount Nombre de points dans la polyline
 */
public record EncodedPolyline(String path, String elevations, String elevationEncoding, int pointCount) {
    
    /**
     * Convertit l'EncodedPolyline en format JSON simple.
     */
    public String toJson() {
        return """
                {
                  "path": "%s",
                  "elevations": "%s",
                  "elevationEncoding": "%s",
                  "pointCount": %d
                }""".formatted(path, elevations, elevationEncoding, pointCount);
    }
}
