package com.github.thecyclistdiary.maps.polyline;

import java.util.List;

/**
 * Encoder pour le format Google Polyline (latitude/longitude uniquement).
 * Utilise l'algorithme standard avec précision 1e5 (~1.1m).
 */
public class PolylineEncoder {
    
    private static final double PRECISION = 1e5;
    private static final int ASCII_OFFSET = 63;
    private static final int FIVE_BIT_MASK = 0x1f;
    private static final int CONTINUATION_BIT = 0x20;
    
    /**
     * Encode une liste de points GPX en polyline Google standard.
     * 
     * @param points Liste des points à encoder
     * @return Polyline encodée au format Google
     */
    public static String encode(List<GpxPoint> points) {
        if (points == null || points.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        long prevLat = 0;
        long prevLon = 0;
        
        for (GpxPoint point : points) {
            // Convertir en entiers avec la précision
            long lat = Math.round(point.lat() * PRECISION);
            long lon = Math.round(point.lon() * PRECISION);
            
            // Delta encoding
            long deltaLat = lat - prevLat;
            long deltaLon = lon - prevLon;
            
            // Encoder les deltas
            encodeValue(deltaLat, result);
            encodeValue(deltaLon, result);
            
            prevLat = lat;
            prevLon = lon;
        }
        
        return result.toString();
    }
    
    /**
     * Encode une valeur signée selon l'algorithme polyline :
     * 1. Left shift de 1 bit
     * 2. Inversion si négatif
     * 3. Découpage en chunks de 5 bits
     * 4. Conversion en ASCII (+63)
     */
    private static void encodeValue(long value, StringBuilder result) {
        // Encoder le signe : left shift et inversion si négatif
        long encoded = value < 0 ? ~(value << 1) : (value << 1);
        
        // Découper en chunks de 5 bits avec bit de continuation
        while (encoded >= CONTINUATION_BIT) {
            int chunk = (int) ((encoded & FIVE_BIT_MASK) | CONTINUATION_BIT);
            result.append((char) (chunk + ASCII_OFFSET));
            encoded >>= 5;
        }
        
        // Dernier chunk sans bit de continuation
        result.append((char) ((int) encoded + ASCII_OFFSET));
    }
}
