package com.stacc.backend.lms.assignment.submission;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Entity
@Table(name = "lms_rubric_assessments")
public class LmsRubricAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_submission_grade_id", nullable = false, updatable = false, unique = true)
    private LmsSubmissionGrade grade;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "lms_rubric_scores", joinColumns = @JoinColumn(name = "lms_rubric_assessment_id"))
    @OrderBy("position")
    private List<LmsRubricScore> scores = new ArrayList<>();

    protected LmsRubricAssessment() {
    }

    LmsRubricAssessment(LmsSubmissionGrade grade) {
        this.grade = grade;
    }

    void replace(List<LmsRubricScore> given) {
        scores.clear();
        scores.addAll(given);
    }

    public Long getId() {
        return id;
    }

    public List<LmsRubricScore> getScores() {
        return List.copyOf(scores);
    }
}
