package com.stacc.backend.lms.ai.saved;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ai_saved_answers")
public class AiSavedAnswer {

    public static final int MAX_QUESTION_LENGTH = 1000;
    public static final int MAX_ANSWER_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lms_course_id", nullable = false, updatable = false)
    private Long lmsCourseId;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Column(nullable = false, length = MAX_QUESTION_LENGTH, updatable = false)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String answer;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_saved_answer_sources", joinColumns = @JoinColumn(name = "ai_saved_answer_id"))
    @OrderBy("position")
    private List<AiSavedSource> sources = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiSavedAnswer() {
    }

    public AiSavedAnswer(Long lmsCourseId, Long userAccountId, String question, String answer, List<AiSavedSource> sources) {
        this.lmsCourseId = Require.notNull(lmsCourseId, "lmsCourseId");
        this.userAccountId = Require.notNull(userAccountId, "userAccountId");
        this.question = Require.text(question, "question", MAX_QUESTION_LENGTH);
        this.answer = Require.text(answer, "answer", MAX_ANSWER_LENGTH);
        this.sources.addAll(Require.notNull(sources, "sources"));
    }

    public Long getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public List<AiSavedSource> getSources() {
        return List.copyOf(sources);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
