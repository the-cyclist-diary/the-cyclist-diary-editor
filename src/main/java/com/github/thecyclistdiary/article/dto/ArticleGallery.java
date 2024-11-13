package com.github.thecyclistdiary.article.dto;

import java.util.List;
import java.util.stream.Collectors;

public record ArticleGallery(List<String> images) implements ArticlePart {
    @Override
    public String toString() {
        return String.format("""
                        {{< gallery class="content-gallery" btn="%d">}}
                        %s
                        {{< /gallery >}}
                        """,
                images().size(),
                images().stream()
                        .map(img -> String.format("{{< img src=\"%s\"> >}}", img))
                        .collect(Collectors.joining("\n")));
    }
}
