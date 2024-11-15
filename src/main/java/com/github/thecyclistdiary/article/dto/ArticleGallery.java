package com.github.thecyclistdiary.article.dto;

import java.util.List;
import java.util.stream.Collectors;

public record ArticleGallery(List<ArticleImage> images) implements ArticlePart {
    @Override
    public String toString() {
        if (images.size() == 1){
            return String.format("![an image from this adventure](%s)", images.getFirst());
        }
        return String.format("""
                        {{< gallery class="content-gallery" btn="%d">}}
                        %s
                        {{< /gallery >}}
                        """,
                images().size(),
                images().stream()
                        .map(img -> String.format("{{< img src=\"%s\" >}}", img.name()))
                        .collect(Collectors.joining("\n")));
    }
}
