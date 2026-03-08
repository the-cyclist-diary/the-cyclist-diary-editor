package com.github.thecyclistdiary.article.action;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UploadResponse(
        String filename,
        @JsonProperty("alreadyPresent") boolean alreadyPresent) {
}
