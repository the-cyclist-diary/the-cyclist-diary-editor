package com.github.thecyclistdiary.maps.polyline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ElevationEncoderTest {
    
    @Test
    void it_should_encode_and_decode_elevations() {
        // Soit une liste de points avec altitudes variées
        List<GpxPoint> points = List.of(
            new GpxPoint(0, 0, 100.0),
            new GpxPoint(0, 0, 110.5),
            new GpxPoint(0, 0, 105.2),
            new GpxPoint(0, 0, 120.8)
        );
        
        // Lorsqu'on encode puis décode
        String encoded = ElevationEncoder.encode(points);
        double[] decoded = ElevationEncoder.decode(encoded);
        
        // Elle doit retrouver les valeurs originales avec la précision attendue (±0.1m)
        assertThat(decoded).hasSize(4);
        assertThat(decoded[0]).isCloseTo(100.0, within(0.1));
        assertThat(decoded[1]).isCloseTo(110.5, within(0.1));
        assertThat(decoded[2]).isCloseTo(105.2, within(0.1));
        assertThat(decoded[3]).isCloseTo(120.8, within(0.1));
    }
    
    @Test
    void it_should_encode_empty_list_as_empty_string() {
        // Soit une liste vide
        List<GpxPoint> points = new ArrayList<>();
        
        // Lorsqu'on encode
        String encoded = ElevationEncoder.encode(points);
        
        // Elle doit retourner une string vide
        assertThat(encoded).isEmpty();
    }
    
    @Test
    void it_should_handle_negative_elevations() {
        // Soit des points avec altitudes négatives (sous le niveau de la mer)
        List<GpxPoint> points = List.of(
            new GpxPoint(0, 0, -50.0),
            new GpxPoint(0, 0, -25.5),
            new GpxPoint(0, 0, 10.0)
        );
        
        // Lorsqu'on encode puis décode
        String encoded = ElevationEncoder.encode(points);
        double[] decoded = ElevationEncoder.decode(encoded);
        
        // Elle doit gérer correctement les valeurs négatives
        assertThat(decoded[0]).isCloseTo(-50.0, within(0.1));
        assertThat(decoded[1]).isCloseTo(-25.5, within(0.1));
        assertThat(decoded[2]).isCloseTo(10.0, within(0.1));
    }
    
    @Test
    void it_should_handle_high_elevations() {
        // Soit des points avec altitudes élevées (haute montagne)
        List<GpxPoint> points = List.of(
            new GpxPoint(0, 0, 2500.0),
            new GpxPoint(0, 0, 3000.5),
            new GpxPoint(0, 0, 3276.7)  // Proche de la limite max
        );
        
        // Lorsqu'on encode puis décode
        String encoded = ElevationEncoder.encode(points);
        double[] decoded = ElevationEncoder.decode(encoded);
        
        // Elle doit gérer correctement les valeurs élevées
        assertThat(decoded[0]).isCloseTo(2500.0, within(0.1));
        assertThat(decoded[1]).isCloseTo(3000.5, within(0.1));
        assertThat(decoded[2]).isCloseTo(3276.7, within(0.1));
    }
    
    @Test
    void it_should_produce_base64_string() {
        // Soit des points quelconques
        List<GpxPoint> points = List.of(
            new GpxPoint(0, 0, 100.0),
            new GpxPoint(0, 0, 200.0)
        );
        
        // Lorsqu'on encode
        String encoded = ElevationEncoder.encode(points);
        
        // Elle doit produire une string Base64 valide
        assertThat(encoded).isNotEmpty();
        assertThat(encoded).matches("^[A-Za-z0-9+/]*={0,2}$");
    }
    
    @Test
    void it_should_maintain_precision_to_one_decimal() {
        // Soit des points avec précision au centimètre
        List<GpxPoint> points = List.of(
            new GpxPoint(0, 0, 100.05),
            new GpxPoint(0, 0, 100.14),
            new GpxPoint(0, 0, 100.26)
        );
        
        // Lorsqu'on encode puis décode
        String encoded = ElevationEncoder.encode(points);
        double[] decoded = ElevationEncoder.decode(encoded);
        
        // Elle doit arrondir au décimètre (0.1m)
        // 100.05 -> 100.1 (1000.5 arrondi à 1001 décimètres)
        // 100.14 -> 100.1 (1001.4 arrondi à 1001 décimètres)
        // 100.26 -> 100.3 (1002.6 arrondi à 1003 décimètres)
        assertThat(decoded[0]).isCloseTo(100.1, within(0.05));
        assertThat(decoded[1]).isCloseTo(100.1, within(0.05));
        assertThat(decoded[2]).isCloseTo(100.3, within(0.05));
    }
}
