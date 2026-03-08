package com.github.thecyclistdiary.maps.polyline;

import java.util.Locale;

/**
 * Représente une polyline encodée avec ses altitudes encodées séparément.
 * 
 * @param path Polyline encodée en Base64 (contient une polyline Google encodée)
 * @param pathEncoding Format d'encodage du path ("base64-polyline")
 * @param elevations Altitudes encodées en Base64 (Int16Array en décimètres)
 * @param elevationEncoding Format d'encodage des altitudes ("base64-int16-decimeters")
 * @param pointCount Nombre de points dans la polyline
 * @param metadata Métadonnées calculées du tracé (distance, dénivelé, etc.)
 */
public record EncodedPolyline(
    String path,
    String pathEncoding,
    String elevations, 
    String elevationEncoding, 
    int pointCount,
    TrackMetadata metadata
) {
    
    /**
     * Convertit l'EncodedPolyline en format JSON.
     */
    public String toJson() {
        return String.format(Locale.US, """
            {
              "path": "%s",
              "pathEncoding": "%s",
              "elevations": "%s",
              "elevationEncoding": "%s",
              "pointCount": %d,
              "metadata": %s
            }""",
                path,
                pathEncoding,
                elevations,
                elevationEncoding,
                pointCount,
                metadata.toJson()
            );
    }
}
