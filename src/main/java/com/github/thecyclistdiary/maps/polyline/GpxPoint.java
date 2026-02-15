package com.github.thecyclistdiary.maps.polyline;

/**
 * Représente un point GPX avec ses coordonnées géographiques et son altitude.
 * 
 * @param lat Latitude en degrés décimaux
 * @param lon Longitude en degrés décimaux
 * @param elevation Altitude en mètres
 */
public record GpxPoint(double lat, double lon, double elevation) {
}
