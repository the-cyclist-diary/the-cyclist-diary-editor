package com.github.thecyclistdiary.maps.polyline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GpxPolylineServiceTest {
    
    private final GpxPolylineService service = new GpxPolylineService();
    
    @Test
    void it_should_parse_gpx_file() throws IOException {
        // Soit un fichier GPX test
        Path gpxFile = Path.of("src/test/resources/test-track.gpx");
        
        // Lorsqu'on parse le fichier
        List<GpxPoint> points = service.parseGpx(gpxFile);
        
        // Elle doit extraire tous les points avec leurs coordonnées et altitudes
        assertThat(points).hasSize(5);
        
        // Vérifier le premier point
        assertThat(points.get(0).lat()).isCloseTo(38.5, within(0.0001));
        assertThat(points.get(0).lon()).isCloseTo(-120.2, within(0.0001));
        assertThat(points.get(0).elevation()).isCloseTo(100.0, within(0.1));
        
        // Vérifier le dernier point
        assertThat(points.get(4).lat()).isCloseTo(46.5, within(0.0001));
        assertThat(points.get(4).lon()).isCloseTo(-130.0, within(0.0001));
        assertThat(points.get(4).elevation()).isCloseTo(115.3, within(0.1));
    }
    
    @Test
    void it_should_encode_gpx_file_to_polyline() throws IOException {
        // Soit un fichier GPX test
        Path gpxFile = Path.of("src/test/resources/big-test-track.gpx");
        
        // Lorsqu'on encode le fichier
        EncodedPolyline encoded = service.encode(gpxFile);
        
        // Elle doit produire une polyline encodée avec les bonnes métadonnées
        assertThat(encoded.path()).isNotEmpty();
        assertThat(encoded.pathEncoding()).isEqualTo("base64-polyline");
        assertThat(encoded.elevations()).isNotEmpty();
        assertThat(encoded.elevationEncoding()).isEqualTo("base64-int16-decimeters");
        assertThat(encoded.pointCount()).isEqualTo(9757);
        assertThat(encoded.metadata()).isNotNull();
    }
    
    @Test
    void it_should_decode_polyline_correctly() throws IOException {
        // Soit un fichier GPX test
        Path gpxFile = Path.of("src/test/resources/test-track.gpx");
        List<GpxPoint> originalPoints = service.parseGpx(gpxFile);
        
        // Lorsqu'on encode puis décode
        EncodedPolyline encoded = service.encode(gpxFile);
        
        // Décoder d'abord la Base64, puis la polyline
        String polylineDecoded = new String(Base64.getDecoder().decode(encoded.path()));
        List<GpxPoint> decodedLatLon = decodePolyline(polylineDecoded);
        double[] decodedElevations = ElevationEncoder.decode(encoded.elevations());
        
        // Elle doit retrouver les coordonnées avec la précision attendue
        assertThat(decodedLatLon).hasSize(originalPoints.size());
        assertThat(decodedElevations).hasSize(originalPoints.size());
        
        for (int i = 0; i < originalPoints.size(); i++) {
            GpxPoint original = originalPoints.get(i);
            GpxPoint decoded = decodedLatLon.get(i);
            
            // Précision 1e5 = ~0.00001° = ~1.1m
            assertThat(decoded.lat()).isCloseTo(original.lat(), within(0.00001));
            assertThat(decoded.lon()).isCloseTo(original.lon(), within(0.00001));
            
            // Précision 1e1 = 0.1m
            assertThat(decodedElevations[i]).isCloseTo(original.elevation(), within(0.1));
        }
    }
    
    @Test
    void it_should_save_as_json(@TempDir Path tempDir) throws IOException {
        // Soit un fichier GPX encodé
        Path gpxFile = Path.of("src/test/resources/test-track.gpx");
        EncodedPolyline encoded = service.encode(gpxFile);
        
        // Lorsqu'on sauvegarde en JSON
        Path outputPath = tempDir.resolve("output.polyline.json");
        service.saveAsJson(encoded, outputPath);
        
        // Elle doit créer un fichier JSON valide
        assertThat(outputPath).exists();
        String json = Files.readString(outputPath);
        
        // Vérifier la structure JSON
        assertThat(json).contains("\"path\":");
        assertThat(json).contains("\"pathEncoding\": \"base64-polyline\"");
        assertThat(json).contains("\"elevations\":");
        assertThat(json).contains("\"elevationEncoding\": \"base64-int16-decimeters\"");
        assertThat(json).contains("\"pointCount\": 5");
    }
    
    @Test
    void it_should_calculate_metadata_correctly() throws IOException {
        // Soit un fichier GPX test
        Path gpxFile = Path.of("src/test/resources/test-track.gpx");
        List<GpxPoint> points = service.parseGpx(gpxFile);
        
        // Lorsqu'on calcule les métadonnées
        TrackMetadata metadata = service.calculateMetadata(points);
        
        // Elle doit calculer distance, dénivelé, altitude min/max
        assertThat(metadata.distanceKm()).isGreaterThan(0);
        assertThat(metadata.elevationGainM()).isCloseTo(26.1, within(0.5));
        assertThat(metadata.elevationLossM()).isCloseTo(11.0, within(0.5));
        assertThat(metadata.minElevationM()).isCloseTo(100.0, within(0.1));
        assertThat(metadata.maxElevationM()).isCloseTo(120.8, within(0.1));
    }
    
    @Test
    void it_should_throw_exception_for_nonexistent_file() {
        // Soit un fichier qui n'existe pas
        Path nonExistent = Path.of("src/test/resources/nonexistent.gpx");
        
        // Lorsqu'on tente de parser
        // Elle doit lever une IOException
        assertThatThrownBy(() -> service.parseGpx(nonExistent))
            .isInstanceOf(IOException.class);
    }
    
    @Test
    void it_should_handle_gpx_without_elevation() throws IOException {
        // Soit un fichier GPX sans données d'altitude
        Path tempFile = Files.createTempFile("test-no-elevation", ".gpx");
        Files.writeString(tempFile, """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <trkseg>
                  <trkpt lat="45.5" lon="-73.5"></trkpt>
                  <trkpt lat="45.6" lon="-73.6"></trkpt>
                </trkseg>
              </trk>
            </gpx>
            """);
        
        try {
            // Lorsqu'on parse le fichier
            List<GpxPoint> points = service.parseGpx(tempFile);
            
            // Elle doit utiliser 0.0 comme altitude par défaut
            assertThat(points).hasSize(2);
            assertThat(points.get(0).elevation()).isEqualTo(0.0);
            assertThat(points.get(1).elevation()).isEqualTo(0.0);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    @Test
    void it_should_calculate_time_metadata_when_timestamps_available() throws IOException {
        // Soit un fichier GPX test avec timestamps
        Path gpxFile = Path.of("src/test/resources/test-track.gpx");
        
        // Lorsqu'on encode le fichier
        EncodedPolyline encoded = service.encode(gpxFile);
        TrackMetadata metadata = encoded.metadata();
        
        // Elle doit calculer la durée et la vitesse moyenne
        assertThat(metadata.hasTimeData()).isTrue();
        assertThat(metadata.durationSeconds()).isEqualTo(7200L); // 2h
        assertThat(metadata.formatDuration()).isEqualTo("02:00:00");
        assertThat(metadata.startTime()).isNotNull();
        assertThat(metadata.endTime()).isNotNull();
        assertThat(metadata.averageSpeedKmh()).isGreaterThan(0);
    }
    
    @Test
    void it_should_include_metadata_in_json(@TempDir Path tempDir) throws IOException {
        // Soit un fichier GPX encodé
        Path gpxFile = Path.of("src/test/resources/test-track.gpx");
        EncodedPolyline encoded = service.encode(gpxFile);
        
        // Lorsqu'on sauvegarde en JSON
        Path outputPath = tempDir.resolve("output.polyline.json");
        service.saveAsJson(encoded, outputPath);
        
        // Elle doit inclure les métadonnées dans le JSON
        String json = Files.readString(outputPath);
        assertThat(json).contains("\"metadata\":");
        assertThat(json).contains("\"distanceKm\":");
        assertThat(json).contains("\"elevationGainM\":");
        assertThat(json).contains("\"elevationLossM\":");
        assertThat(json).contains("\"minElevationM\":");
        assertThat(json).contains("\"maxElevationM\":");
        assertThat(json).contains("\"durationSeconds\":");
        assertThat(json).contains("\"averageSpeedKmh\":");
    }
    
    /**
     * Helper method to decode a Google Polyline string back to coordinates.
     * Implements the reverse of PolylineEncoder.encode().
     */
    private List<GpxPoint> decodePolyline(String encoded) {
        List<GpxPoint> points = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lon = 0;
        
        while (index < encoded.length()) {
            // Décoder latitude
            int result = 0;
            int shift = 0;
            int b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            
            int deltaLat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += deltaLat;
            
            // Décoder longitude
            result = 0;
            shift = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            
            int deltaLon = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lon += deltaLon;
            
            points.add(new GpxPoint(lat / 1e5, lon / 1e5, 0));
        }
        
        return points;
    }
    
    private static class ArrayList<E> extends java.util.ArrayList<E> {}
}
