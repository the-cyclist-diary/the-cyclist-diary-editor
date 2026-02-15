package com.github.thecyclistdiary.maps.polyline;

import java.time.ZonedDateTime;

/**
 * Métadonnées calculées pour un tracé GPX.
 * 
 * @param distanceKm Distance totale en kilomètres
 * @param elevationGainM Dénivelé positif cumulé en mètres
 * @param elevationLossM Dénivelé négatif cumulé en mètres
 * @param minElevationM Altitude minimale en mètres
 * @param maxElevationM Altitude maximale en mètres
 * @param durationSeconds Durée totale en secondes (si timestamps disponibles)
 * @param startTime Date/heure de début (si disponible)
 * @param endTime Date/heure de fin (si disponible)
 * @param averageSpeedKmh Vitesse moyenne en km/h (si durée disponible)
 */
public record TrackMetadata(
    double distanceKm,
    double elevationGainM,
    double elevationLossM,
    double minElevationM,
    double maxElevationM,
    Long durationSeconds,
    ZonedDateTime startTime,
    ZonedDateTime endTime,
    Double averageSpeedKmh
) {
    /**
     * Constructeur pour un tracé sans informations temporelles.
     */
    public TrackMetadata(
        double distanceKm,
        double elevationGainM,
        double elevationLossM,
        double minElevationM,
        double maxElevationM
    ) {
        this(distanceKm, elevationGainM, elevationLossM, minElevationM, maxElevationM, 
             null, null, null, null);
    }
    
    /**
     * Retourne true si les informations temporelles sont disponibles.
     */
    public boolean hasTimeData() {
        return durationSeconds != null && startTime != null && endTime != null;
    }
    
    /**
     * Formatte la durée en format lisible (HH:MM:SS).
     */
    public String formatDuration() {
        if (durationSeconds == null) {
            return null;
        }
        long hours = durationSeconds / 3600;
        long minutes = (durationSeconds % 3600) / 60;
        long seconds = durationSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
