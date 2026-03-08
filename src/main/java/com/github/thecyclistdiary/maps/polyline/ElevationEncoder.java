package com.github.thecyclistdiary.maps.polyline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;

/**
 * Encoder pour les altitudes au format Base64 Int16Array.
 * Utilise une précision de 1e1 (décimètres) pour un bon ratio compression/précision.
 */
public class ElevationEncoder {
    
    private static final double PRECISION = 1e1; // 0.1 mètre = 1 décimètre
    
    /**
     * Encode une liste d'altitudes en Base64 (Int16Array).
     * Range supporté : -3276.8m à +3276.7m
     * 
     * @param points Liste des points contenant les altitudes
     * @return String Base64 représentant un tableau d'entiers 16 bits
     */
    public static String encode(List<GpxPoint> points) {
        if (points == null || points.isEmpty()) {
            return "";
        }
        
        // Allouer un buffer pour les entiers 16 bits (2 bytes par point)
        ByteBuffer buffer = ByteBuffer.allocate(points.size() * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        for (GpxPoint point : points) {
            // Convertir l'altitude en décimètres et arrondir
            short elevationDecimeters = (short) Math.round(point.elevation() * PRECISION);
            buffer.putShort(elevationDecimeters);
        }
        
        // Encoder en Base64
        return Base64.getEncoder().encodeToString(buffer.array());
    }
    
    /**
     * Décode une string Base64 en tableau d'altitudes (pour les tests).
     * 
     * @param base64String String Base64 à décoder
     * @return Tableau d'altitudes en mètres
     */
    public static double[] decode(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return new double[0];
        }
        
        byte[] bytes = Base64.getDecoder().decode(base64String);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        int count = bytes.length / 2;
        double[] elevations = new double[count];
        
        for (int i = 0; i < count; i++) {
            short elevationDecimeters = buffer.getShort();
            elevations[i] = elevationDecimeters / PRECISION;
        }
        
        return elevations;
    }
}
