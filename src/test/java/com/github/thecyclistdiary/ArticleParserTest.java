package com.github.thecyclistdiary;

import com.github.thecyclistdiary.article.dto.Article;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssue;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Disabled
class ArticleParserTest {

    @Test
    void it_should_parse_issue_correctly() throws IOException {
        // Soit un parser et une issue github
        Path resourceDirectory = Paths.get("src","test","resources");
        File issueFile = resourceDirectory.resolve("issue.txt").toFile();
        String fileContent;
        try (BufferedReader reader = new BufferedReader(new FileReader(issueFile))){
            fileContent = reader.lines().collect(Collectors.joining("\n"));
        }
        GHIssue ghIssue = Mockito.mock(GHIssue.class);
        String title = "test title";
        when(ghIssue.getTitle()).thenReturn(title);
        when(ghIssue.getBody()).thenReturn(fileContent);

        // Lorsqu'une issue est lue
        Article actualArticle = ArticleParser.parse(ghIssue);

        // Elle doit être correctement parsée
        File expectedArticle = resourceDirectory.resolve("article.md").toFile();
        String expectedArticleContent;
        try (BufferedReader reader = new BufferedReader(new FileReader(expectedArticle))){
            expectedArticleContent = reader.lines().collect(Collectors.joining("\n"));
        }
        assertThat(actualArticle.title()).isEqualTo(title);
        assertThat(actualArticle.toString()).isEqualTo(String.format(expectedArticleContent,
                LocalDate.now()));
    }

}