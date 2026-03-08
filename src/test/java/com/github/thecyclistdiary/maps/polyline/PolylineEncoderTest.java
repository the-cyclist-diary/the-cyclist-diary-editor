package com.github.thecyclistdiary.maps.polyline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolylineEncoderTest {
    
    @Test
    void it_should_encode_simple_polyline() {
        // Soit une liste de 3 points simples
        List<GpxPoint> points = List.of(
            new GpxPoint(38.5, -120.2, 0),
            new GpxPoint(40.7, -120.95, 0),
            new GpxPoint(43.252, -126.453, 0)
        );
        
        // Lorsqu'on encode la polyline
        String encoded = PolylineEncoder.encode(points);
        
        // Elle doit produire une string non vide avec uniquement des caractères ASCII printables
        assertThat(encoded).isNotEmpty();
        assertThat(encoded).matches("^[?@A-Z\\[\\\\\\]\\^_`a-z{\\|}~]+$");
        
        // Calcul manuel pour vérification (premier point):
        // lat = 38.5 * 1e5 = 3850000
        // lon = -120.2 * 1e5 = -12020000
        // Les deltas pour le premier point sont les valeurs absolues
        // L'encodage doit commencer par ces valeurs encodées
        assertThat(encoded).startsWith("_p~i"); // Préfixe connu pour ces coordonnées
    }
    
    @Test
    void it_should_encode_empty_list_as_empty_string() {
        // Soit une liste vide
        List<GpxPoint> points = new ArrayList<>();
        
        // Lorsqu'on encode
        String encoded = PolylineEncoder.encode(points);
        
        // Elle doit retourner une string vide
        assertThat(encoded).isEmpty();
    }
    
    @Test
    void it_should_encode_single_point() {
        // Soit un seul point
        List<GpxPoint> points = List.of(new GpxPoint(45.5, -73.5, 0));
        
        // Lorsqu'on encode
        String encoded = PolylineEncoder.encode(points);
        
        // Elle doit produire une string non vide
        assertThat(encoded).isNotEmpty();
        assertThat(encoded.length()).isGreaterThan(2);
    }
    
    @Test
    void it_should_handle_negative_coordinates() {
        // Soit des points avec coordonnées négatives
        List<GpxPoint> points = List.of(
            new GpxPoint(-33.8688, 151.2093, 0),  // Sydney
            new GpxPoint(-34.6037, 58.3816, 0)     // Buenos Aires
        );
        
        // Lorsqu'on encode
        String encoded = PolylineEncoder.encode(points);
        
        // Elle doit encoder sans erreur
        assertThat(encoded).isNotEmpty();
    }
    
    @Test
    void it_should_handle_close_points() {
        // Soit des points très proches (delta encoding efficace)
        List<GpxPoint> points = List.of(
            new GpxPoint(48.8566, 2.3522, 0),
            new GpxPoint(48.8567, 2.3523, 0),
            new GpxPoint(48.8568, 2.3524, 0)
        );
        
        // Lorsqu'on encode
        String encoded = PolylineEncoder.encode(points);
        
        // Elle doit produire une string compacte
        assertThat(encoded).isNotEmpty();
        // Les points proches devraient produire un encodage plus court que des points éloignés
        assertThat(encoded.length()).isLessThan(30);
    }
}
