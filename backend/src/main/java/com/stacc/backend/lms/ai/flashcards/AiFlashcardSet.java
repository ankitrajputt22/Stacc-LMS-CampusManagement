package com.stacc.backend.lms.ai.flashcards;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ai_flashcard_sets")
public class AiFlashcardSet {

    public static final int MAX_TITLE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lms_course_id", nullable = false, updatable = false)
    private Long lmsCourseId;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @OneToMany(mappedBy = "set", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<AiFlashcard> cards = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiFlashcardSet() {
    }

    public AiFlashcardSet(Long lmsCourseId, Long userAccountId, String title) {
        this.lmsCourseId = Require.notNull(lmsCourseId, "lmsCourseId");
        this.userAccountId = Require.notNull(userAccountId, "userAccountId");
        String trimmed = Require.text(title, "title", 1000);
        this.title = trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
    }

    public void addCard(String front, String back, String sourceType, long sourceId, String sourceTitle, Long pageId, LocalDate today) {
        cards.add(new AiFlashcard(this, cards.size() + 1, front, back, sourceType, sourceId, sourceTitle, pageId, today));
    }

    public Long getId() {
        return id;
    }

    public Long getLmsCourseId() {
        return lmsCourseId;
    }

    public Long getUserAccountId() {
        return userAccountId;
    }

    public String getTitle() {
        return title;
    }

    public List<AiFlashcard> getCards() {
        return List.copyOf(cards);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
