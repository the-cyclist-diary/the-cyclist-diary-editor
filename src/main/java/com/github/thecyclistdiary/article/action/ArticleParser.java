package com.github.thecyclistdiary.article.action;

import com.github.thecyclistdiary.article.dto.*;
import org.kohsuke.github.GHIssue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArticleParser {
    private static final Pattern GPX_PATTERN = Pattern.compile(":gpx:");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[.*]");
    private static final Pattern GITHUB_ATTACHMENT_PATTERN = Pattern.compile("\\((.*)\\)");
    public static final Pattern DATE_TAG_PATTERN = Pattern.compile("### Date de l'article");
    public static final Pattern CONTENT_TAG_PATTERN = Pattern.compile("### Contenu");
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final String NO_RESPONSE = "_No response_";

    public static Article parse(GHIssue issue) {
        AtomicReference<String> gpxUrl = new AtomicReference<>();
        AtomicReference<String> date = new AtomicReference<>();
        List<ArticlePart> articleParts = new ArrayList<>();
        AtomicBoolean isParsingDate = new AtomicBoolean(false);
        AtomicBoolean isParsingContent = new AtomicBoolean(false);

        issue.getBody().lines()
                .forEach(line -> {
                    parseLine(line, gpxUrl, date, articleParts, isParsingDate, isParsingContent);
                });

        return new Article(issue.getTitle(), articleParts, gpxUrl.get(), date.get());
    }

    private static void parseLine(String line, AtomicReference<String> gpxUrl, AtomicReference<String> date, List<ArticlePart> articleParts,
                                  AtomicBoolean isParsingDate, AtomicBoolean isParsingContent) {
        if (isParsingDate.get()) {
            tryToParseDate(line, isParsingDate, date);
        } else if (isParsingContent.get()) {
            parseContent(line, gpxUrl, articleParts);
        } else {
            parseCommonLine(line, isParsingDate, isParsingContent);
        }
    }

    private static void parseCommonLine(String line,
                                        AtomicBoolean isParsingDate, AtomicBoolean isParsingContent) {
        if (DATE_TAG_PATTERN.matcher(line).find()) {
            isParsingDate.set(true);
        } else if (CONTENT_TAG_PATTERN.matcher(line).find()) {
            isParsingContent.set(true);
        }
    }

    private static void parseContent(String line, AtomicReference<String> gpxUrl, List<ArticlePart> articleParts) {
        if (GPX_PATTERN.matcher(line).find()) {
            parseGpxUrl(line).ifPresent(gpxUrl::set);
        } else if (IMAGE_PATTERN.matcher(line).find()) {
            parseImageUrl(line).ifPresent(imageUrl -> processImage(imageUrl, articleParts));
        } else {
            articleParts.add(new ArticleLine(line));
        }
    }

    private static void tryToParseDate(String line, AtomicBoolean isParsingDate, AtomicReference<String> date) {
        try {
            DATE_FORMAT.parse(line);
            date.set(line.trim());
            isParsingDate.set(false);
        } catch (ParseException e) {
            if (NO_RESPONSE.equals(line)) {
                isParsingDate.set(false);
            }
            System.err.println("La date ne peut pas être parsée");
        }
    }

    private static void processImage(String imageUrl, List<ArticlePart> articleParts) {
        if (articleParts.getLast() instanceof ArticleGallery(List<ArticleImage> images)) {
            images.add(new ArticleImage(imageUrl, getId()));
        } else {
            List<ArticleImage> images = new ArrayList<>();
            images.add(new ArticleImage(imageUrl, getId()));
            ArticleGallery gallery = new ArticleGallery(images);
            articleParts.add(gallery);
        }
    }

    private static String getId() {
        return String.format("%s.jpg", UUID.randomUUID());
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
