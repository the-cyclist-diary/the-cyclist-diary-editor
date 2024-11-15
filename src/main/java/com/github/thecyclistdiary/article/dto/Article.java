package com.github.thecyclistdiary.article.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public record Article(String title, List<ArticlePart> articleParts, String gpxUrl, String date) {
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
                date != null ? date : LocalDate.now(),
                getBodyAsString()
        );
    }

    public String getBodyAsString() {
        return articleParts.stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
    }
}
