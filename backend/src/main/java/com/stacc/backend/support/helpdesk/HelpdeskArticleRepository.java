package com.stacc.backend.support.helpdesk;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HelpdeskArticleRepository extends JpaRepository<HelpdeskArticle, Long> {

    /** Every article with its category, for the admins: by category name, then title. */
    @Query("select article from HelpdeskArticle article join fetch article.category category order by category.name, article.title, article.id")
    List<HelpdeskArticle> findAllWithCategory();

    /** The published articles of an active category, newest published first. */
    @Query("""
            select article from HelpdeskArticle article join fetch article.category category
            where category.id = :categoryId and category.active = true and article.status = 'PUBLISHED'
            order by article.publishedAt desc, article.id desc
            """)
    List<HelpdeskArticle> findPublishedForCategory(@Param("categoryId") Long categoryId);

    @Query("select article from HelpdeskArticle article join fetch article.category where article.id = :articleId")
    Optional<HelpdeskArticle> findWithCategoryById(@Param("articleId") Long articleId);
}
