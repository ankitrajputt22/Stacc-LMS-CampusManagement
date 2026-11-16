package com.stacc.backend.support.helpdesk;

import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-help articles of the helpdesk. Requesters read published articles of active categories, ranked by how many
 * words of their ticket subject they share; admins write, publish, and archive them. Nothing is deleted.
 */
@Service
public class HelpdeskArticleService {

    public static final String ARTICLE_UNAVAILABLE = "Article not found or unavailable.";
    public static final String CATEGORY_INVALID = "Choose a helpdesk category.";
    public static final int MAX_SUGGESTIONS = 5;

    // Common words that say nothing about the problem.
    private static final Set<String> IGNORED = Set.of("the", "and", "for", "with", "not", "can", "cannot", "how", "what",
            "why", "when", "does", "doesn", "don", "from", "into", "have", "has", "was", "are", "this", "that", "my", "our");

    private final HelpdeskArticleRepository articles;
    private final HelpdeskCategoryRepository categories;
    private final Clock clock;

    public HelpdeskArticleService(HelpdeskArticleRepository articles, HelpdeskCategoryRepository categories, Clock clock) {
        this.articles = articles;
        this.categories = categories;
        this.clock = clock;
    }

    /**
     * The published articles of an active category for someone writing a ticket: with words, the ones sharing the most
     * of them first and none that share none; without, the newest. At most {@value #MAX_SUGGESTIONS}.
     */
    @Transactional(readOnly = true)
    public List<HelpdeskArticle> suggestions(Long categoryId, String text) {
        List<HelpdeskArticle> found = categoryId == null ? List.of() : articles.findPublishedForCategory(categoryId);
        Set<String> words = words(text);
        if (words.isEmpty()) {
            return found.stream().limit(MAX_SUGGESTIONS).toList();
        }
        return found.stream()
                .filter(article -> article.matches(words) > 0)
                .sorted(Comparator.comparingInt((HelpdeskArticle article) -> article.matches(words)).reversed())
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    /** A published article of an active category; anything else answers the same 404. */
    @Transactional(readOnly = true)
    public HelpdeskArticle published(Long articleId) {
        return (articleId == null ? java.util.Optional.<HelpdeskArticle>empty() : articles.findWithCategoryById(articleId))
                .filter(article -> article.getStatus() == HelpdeskArticle.Status.PUBLISHED && article.getCategory().isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ARTICLE_UNAVAILABLE));
    }

    @Transactional(readOnly = true)
    public List<HelpdeskArticle> all() {
        return articles.findAllWithCategory();
    }

    /** Adds a draft article. */
    @Transactional
    public HelpdeskArticle add(Long adminAccountId, Long categoryId, String title, String body) {
        HelpdeskCategory category = category(categoryId);
        return articles.saveAndFlush(valid(() -> new HelpdeskArticle(category, title, body, adminAccountId)));
    }

    @Transactional
    public HelpdeskArticle edit(Long adminAccountId, Long articleId, Long categoryId, String title, String body) {
        HelpdeskArticle article = article(articleId);
        HelpdeskCategory category = category(categoryId);
        valid(() -> {
            article.edit(category, title, body, adminAccountId);
            return article;
        });
        return articles.saveAndFlush(article);
    }

    @Transactional
    public HelpdeskArticle publish(Long adminAccountId, Long articleId) {
        HelpdeskArticle article = article(articleId);
        valid(() -> {
            article.publish(clock.instant(), adminAccountId);
            return article;
        });
        return articles.saveAndFlush(article);
    }

    @Transactional
    public HelpdeskArticle archive(Long adminAccountId, Long articleId) {
        HelpdeskArticle article = article(articleId);
        article.archive(adminAccountId);
        return articles.saveAndFlush(article);
    }

    /** The distinct lower-case words of at least three letters, without the common ones. */
    static Set<String> words(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= 3 && !IGNORED.contains(word))
                .limit(20)
                .collect(Collectors.toSet());
    }

    private HelpdeskArticle article(Long articleId) {
        return (articleId == null ? java.util.Optional.<HelpdeskArticle>empty() : articles.findWithCategoryById(articleId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ARTICLE_UNAVAILABLE));
    }

    private HelpdeskCategory category(Long categoryId) {
        return (categoryId == null ? java.util.Optional.<HelpdeskCategory>empty() : categories.findById(categoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, CATEGORY_INVALID));
    }

    private static <T> T valid(Supplier<T> make) {
        try {
            return make.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (IllegalStateException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage());
        }
    }
}
