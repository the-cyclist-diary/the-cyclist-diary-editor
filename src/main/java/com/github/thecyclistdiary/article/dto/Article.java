package com.github.thecyclistdiary.article.dto;

import java.time.LocalDate;
import java.util.List;

public record Article(String folder, String title, String body, List<String> imageUrls, String gpxUrl) {
    @Override
    public String toString() {
        return String.format("""
                        +++
                        title = "%s"
                        date = "%s"
                        draft = "false"
                        +++
                        
                        %s
                        """,
                title,
                LocalDate.now(),
                body
        );
    }
}
