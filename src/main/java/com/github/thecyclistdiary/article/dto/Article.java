package com.github.thecyclistdiary.article.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public record Article(String folder, String title, List<ArticlePart> articleParts, String gpxUrl) {
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
                getBodyAsString()
        );
    }

    public String getBodyAsString() {
        return articleParts.stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
    }
}
