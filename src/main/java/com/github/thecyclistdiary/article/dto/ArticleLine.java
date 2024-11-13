package com.github.thecyclistdiary.article.dto;

public record ArticleLine(String line) implements ArticlePart {
    @Override
    public String toString() {
        return line;
    }
}
