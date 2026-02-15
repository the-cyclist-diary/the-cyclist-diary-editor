package com.github.thecyclistdiary.maps.polyline;

import java.time.ZonedDateTime;

/**
 * Représente un point GPX avec ses coordonnées géographiques et son altitude.
 * 
 * @param lat Latitude en degrés décimaux
 * @param lon Longitude en degrés décimaux
 * @param elevation Altitude en mètres
 * @param time Horodatage du point (peut être null)
 */
public record GpxPoint(double lat, double lon, double elevation, ZonedDateTime time) {
    
    /**
     * Constructeur sans timestamp (pour rétrocompatibilité).
     */
    public GpxPoint(double lat, double lon, double elevation) {
        this(lat, lon, elevation, null);
    }
}
