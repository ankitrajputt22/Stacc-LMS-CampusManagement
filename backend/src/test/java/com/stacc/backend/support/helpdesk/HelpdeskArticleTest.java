package com.stacc.backend.support.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HelpdeskArticleTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");
    private static final HelpdeskCategory IT = new HelpdeskCategory("IT", "IT support", null);

    private static HelpdeskArticle article(long id, String title, String body) {
        HelpdeskArticle article = new HelpdeskArticle(IT, title, body, 1L);
        ReflectionTestUtils.setField(article, "id", id);
        article.publish(NOW, 1L);
        return article;
    }

    @Test
    void anArticleIsPublishedOnceAndAnArchivedOneNeverChanges() {
        HelpdeskArticle article = new HelpdeskArticle(IT, "Reset your Wi-Fi password", "Open the portal.", 1L);
        article.publish(NOW, 1L);
        article.publish(NOW.plusSeconds(60), 1L);
        assertThat(article.getPublishedAt()).isEqualTo(NOW);
        article.archive(1L);
        assertThatThrownBy(() -> article.edit(IT, "Changed", "Text", 1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> article.publish(NOW, 1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void suggestionsShareTheMostWordsFirstAndSkipCommonOnes() {
        HelpdeskArticleRepository articles = mock(HelpdeskArticleRepository.class);
        HelpdeskArticle wifi = article(1, "Wi-Fi keeps disconnecting", "Forget the network and join campus Wi-Fi again.");
        HelpdeskArticle password = article(2, "Reset your Wi-Fi password", "Use the password reset portal.");
        HelpdeskArticle printer = article(3, "Printer queue stuck", "Restart the print queue.");
        when(articles.findPublishedForCategory(5L)).thenReturn(List.of(printer, password, wifi));
        HelpdeskArticleService service = new HelpdeskArticleService(articles, mock(HelpdeskCategoryRepository.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.suggestions(5L, "My wifi password is not working")).containsExactly(password);
        assertThat(service.suggestions(5L, "wi-fi disconnecting in the hostel")).first().isEqualTo(wifi);
        assertThat(service.suggestions(5L, "  ")).containsExactly(printer, password, wifi);
        assertThat(HelpdeskArticleService.words("How do I fix the Printer?")).containsExactlyInAnyOrder("fix", "printer");
    }
}
