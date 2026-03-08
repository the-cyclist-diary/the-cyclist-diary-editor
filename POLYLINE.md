# Encodage Polyline avec Altitude

## Vue d'ensemble

Cette fonctionnalité permet de générer des polylines encodées avec altitude à partir de fichiers GPX. Les polylines sont générées automatiquement en parallèle des cartes PNG lors du processing des fichiers GPX modifiés.

## Format de sortie

Pour chaque fichier `.gpx`, un fichier `.polyline.json` est généré avec la structure suivante :

```json
{
  "path": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
  "elevations": "A+QDvADwQDxQ",
  "elevationEncoding": "base64-int16-decimeters",
  "pointCount": 5,
  "metadata": {
    "distanceKm": 12.456,
    "elevationGainM": 345.6,
    "elevationLossM": 123.4,
    "minElevationM": 100.0,
    "maxElevationM": 445.6,
    "durationSeconds": 7200,
    "durationFormatted": "02:00:00",
    "startTime": "2024-01-15T10:00:00Z",
    "endTime": "2024-01-15T12:00:00Z",
    "averageSpeedKmh": 6.23
  }
}
```

### Champs principaux

- **`path`** : Polyline encodée au format Google standard (latitude/longitude)
  - Compatible avec Google Maps, Leaflet, Mapbox, OpenLayers
  - Précision : 1e5 (~1.1m)
  
- **`elevations`** : Altitudes encodées en Base64 (Int16Array)
  - Format : entiers 16 bits signés en little-endian
  - Précision : 1e1 (0.1m = 1 décimètre)
  - Range : -3276.8m à +3276.7m
  
- **`elevationEncoding`** : Format d'encodage (toujours `"base64-int16-decimeters"`)
  
- **`pointCount`** : Nombre de points dans la polyline

### Métadonnées du tracé

- **`distanceKm`** : Distance totale en kilomètres (calculée avec formule de Haversine)
- **`elevationGainM`** : Dénivelé positif cumulé en mètres (D+)
- **`elevationLossM`** : Dénivelé négatif cumulé en mètres (D-)
- **`minElevationM`** : Altitude minimale en mètres
- **`maxElevationM`** : Altitude maximale en mètres
- **`durationSeconds`** : Durée totale en secondes (si timestamps disponibles dans le GPX)
- **`durationFormatted`** : Durée formatée au format HH:MM:SS (si disponible)
- **`startTime`** : Date/heure de début (ISO 8601, si disponible)
- **`endTime`** : Date/heure de fin (ISO 8601, si disponible)
- **`averageSpeedKmh`** : Vitesse moyenne en km/h (si durée disponible)

## Taille des données

Pour 1000 points d'un tracé cycliste typique :
- **Polyline + altitudes** : ~6-7 KB
- **Comparé à JSON brut** : ~50 KB
- **Ratio de compression** : ~7-8x

## Décodage JavaScript

### Décoder la polyline (lat/lon)

Utiliser une librairie existante :

```javascript
// Avec @mapbox/polyline
import polyline from '@mapbox/polyline';
const coordinates = polyline.decode('_p~iF~ps|U_ulL...');
// coordinates = [[38.5, -120.2], [40.7, -120.95], ...]
```

### Décoder les altitudes

```javascript
function decodeElevations(base64String) {
    const binary = atob(base64String);
    const bytes = new Uint8Array(binary.length);
    
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    
    const int16Array = new Int16Array(bytes.buffer);
    return Array.from(int16Array).map(e => e / 10); // Convertir en mètres
}

const elevations = decodeElevations(data.elevations);
// elevations = [100.0, 110.5, 105.2, 120.8, ...]
```

### Combiner les données

```javascript
const path = polyline.decode(data.path);
const elevations = decodeElevations(data.elevations);

const points = path.map(([lat, lon], i) => ({
    lat,
    lon,
    elevation: elevations[i]
}));
```

## Exemple d'utilisation avec Leaflet

```javascript
// Charger les données
const response = await fetch('route.polyline.json');
const data = await response.json();

// Décoder
const path = polyline.decode(data.path);
const elevations = decodeElevations(data.elevations);

// Afficher sur la carte
const latlngs = path.map(([lat, lon]) => [lat, lon]);
L.polyline(latlngs, { color: 'blue' }).addTo(map);

// Créer un profil d'élévation
const profile = path.map(([lat, lon], i) => ({
    distance: calculateDistance(i), // À implémenter
    elevation: elevations[i]
}));
```

## Architecture

### Classes principales

- **`GpxPoint`** : Record pour un point GPX (lat, lon, elevation, time optionnel)
- **`EncodedPolyline`** : Record pour une polyline encodée avec métadonnées
- **`TrackMetadata`** : Record contenant les statistiques du tracé (distance, dénivelé, durée, etc.)
- **`PolylineEncoder`** : Encode lat/lon au format Google Polyline
- **`ElevationEncoder`** : Encode les altitudes en Base64 Int16Array
- **`GpxPolylineService`** : Service principal pour parser GPX, calculer métadonnées et générer polylines

### Calculs effectués

- **Distance** : Formule de Haversine pour calculer la distance entre points GPS
- **Dénivelé** : Cumul des différences d'altitude positives (D+) et négatives (D-)
- **Altitude min/max** : Recherche des valeurs extrêmes dans le tracé
- **Durée** : Différence entre premier et dernier timestamp (si disponible)
- **Vitesse moyenne** : Distance / durée (si timestamps disponibles)

### Intégration

La génération de polylines est intégrée dans le workflow existant :
- `GitAwareGpxToMapWalker` génère automatiquement les polylines lors du traitement des fichiers GPX modifiés
- Les fichiers `.polyline.json` sont créés dans le même répertoire que les fichiers `.gpx`
- Les fichiers JSON sont commités avec les cartes PNG

## Tests

Exécuter les tests :

```bash
./mvnw test -Dtest="*Polyline*,*Elevation*"
```

Les tests couvrent :
- Encodage/décodage de polylines
- Encodage/décodage d'altitudes
- Parsing de fichiers GPX avec et sans timestamps
- Calcul de métadonnées (distance, dénivelé, durée)
- Cycle complet GPX → JSON avec vérification des valeurs
- Cas limites (listes vides, coordonnées négatives, hautes altitudes)

## Dépendances

- **JPX** (io.jenetics:jpx:3.2.0) : Parser GPX léger et performant
- Classes Java standard pour Base64 et ByteBuffer

## Avantages de cette approche

✅ **Compatibilité maximale** : Polyline standard fonctionnelle avec toutes les librairies de mapping  
✅ **Compression optimale** : ~6-7 KB pour 1000 points vs ~50 KB en JSON  
✅ **Décodage simple** : Librairies existantes + 10 lignes de JavaScript  
✅ **Précision adaptée** : 1e5 pour coordonnées, 1e1 pour altitude  
✅ **Métadonnées complètes** : Distance, dénivelé, durée, vitesse calculés automatiquement  
✅ **Extensible** : Format facilement extensible (timestamps, cadence, rythme cardiaque)  
✅ **Tests complets** : 21 tests unitaires couvrant tous les cas d'usage
