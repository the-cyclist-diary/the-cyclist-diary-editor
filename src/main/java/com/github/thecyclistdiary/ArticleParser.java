package com.github.thecyclistdiary;

import com.github.thecyclistdiary.article.dto.*;
import org.kohsuke.github.GHIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ArticleParser {
    private static final Pattern adventurePattern = Pattern.compile(":adventure:");
    private static final Pattern gpxPattern = Pattern.compile(":gpx:");
    private static final Pattern imagePattern = Pattern.compile("!\\[.*\\]");
    private static final Pattern GITHUB_ATTACHMENT_PATTERN = Pattern.compile("\\((.*)\\)");

    public static Article parse(GHIssue issue) {
        AtomicReference<String> folder = new AtomicReference<>("");
        List<String> imageUrls = new ArrayList<>();
        AtomicReference<String> gpxUrl = new AtomicReference<>();
        List<ArticlePart> articleParts = new ArrayList<>();

        issue.getBody().lines()
                .forEach(line -> {
                    if (adventurePattern.matcher(line).find()) {
                        folder.set(adventurePattern.split(line)[1].trim());
                    } else if (gpxPattern.matcher(line).find()) {
                        parseGpxUrl(line).ifPresent(gpxUrl::set);
                    } else if (imagePattern.matcher(line).find()) {
                        parseImageUrl(line).ifPresent(imageUrl -> {
                            processImage(imageUrl, imageUrls, articleParts);
                        });
                    } else {
                        articleParts.add(new ArticleLine(line));
                    }
                });

        String body = articleParts.stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));

        return new Article(folder.get(), issue.getTitle(), body, imageUrls, gpxUrl.get());
    }

    private static void processImage(String imageUrl, List<String> imageUrls, List<ArticlePart> articleParts) {
        imageUrls.add(imageUrl);
        if (articleParts.getLast() instanceof ArticleGallery gallery) {
            gallery.images().add(imageUrl);
        } else {
            List<String> images = new ArrayList<>();
            images.add(imageUrl);
            ArticleGallery gallery = new ArticleGallery(images);
            articleParts.add(gallery);
        }
    }

    private static Optional<String> parseImageUrl(String imageTag) {
        Matcher matcher = GITHUB_ATTACHMENT_PATTERN.matcher(imageTag);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private static Optional<String> parseGpxUrl(String gpxTag) {
        Matcher matcher = GITHUB_ATTACHMENT_PATTERN.matcher(gpxTag);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

}
