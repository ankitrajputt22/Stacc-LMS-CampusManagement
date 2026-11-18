package com.stacc.backend.academic.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.identity.faculty.FacultyProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "course_results")
public class CourseResult {

    public static final int MARK_DECIMAL_PLACES = 2;
    public static final BigDecimal MAX_MARKS_LIMIT = new BigDecimal("999999.99");
    public static final int MAX_GRADE_CODE_LENGTH = 20;
    public static final int MAX_REMARKS_LENGTH = 2000;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_enrollment_id", nullable = false, unique = true, updatable = false)
    private CourseEnrollment courseEnrollment;

    @Column(name = "marks_obtained", nullable = false, precision = 8, scale = MARK_DECIMAL_PLACES)
    private BigDecimal marksObtained;

    @Column(name = "max_marks", nullable = false, precision = 8, scale = MARK_DECIMAL_PLACES)
    private BigDecimal maxMarks;

    @Column(name = "grade_code", length = MAX_GRADE_CODE_LENGTH)
    private String gradeCode;

    @Column(length = MAX_REMARKS_LENGTH)
    private String remarks;

    @Column(name = "internal_marks", precision = 8, scale = MARK_DECIMAL_PLACES)
    private BigDecimal internalMarks;

    @Column(name = "internal_max", precision = 8, scale = MARK_DECIMAL_PLACES)
    private BigDecimal internalMax;

    @Column(name = "external_marks", precision = 8, scale = MARK_DECIMAL_PLACES)
    private BigDecimal externalMarks;

    @Column(name = "external_max", precision = 8, scale = MARK_DECIMAL_PLACES)
    private BigDecimal externalMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseResultStatus status = CourseResultStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_faculty_profile_id", nullable = false)
    private FacultyProfile recordedByFacultyProfile;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseResult() {
    }

    public CourseResult(CourseEnrollment courseEnrollment, FacultyProfile recordedBy, BigDecimal marksObtained,
            BigDecimal maxMarks, String gradeCode, String remarks) {
        this.courseEnrollment = Require.notNull(courseEnrollment, "courseEnrollment");
        apply(recordedBy, marksObtained, maxMarks, gradeCode, remarks);
    }

    public void record(FacultyProfile recordedBy, BigDecimal marksObtained, BigDecimal maxMarks, String gradeCode,
            String remarks) {
        apply(recordedBy, marksObtained, maxMarks, gradeCode, remarks);
    }

    public void publish(Instant at) {
        if (isPublished()) {
            return;
        }
        this.publishedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
        this.status = CourseResultStatus.PUBLISHED;
    }

    public boolean isPublished() {
        return status == CourseResultStatus.PUBLISHED;
    }

    public static String gradeCodeOrNull(String gradeCode) {
        if (gradeCode == null || gradeCode.isBlank()) {
            return null;
        }
        String normalized = WHITESPACE.matcher(gradeCode.strip()).replaceAll(" ");
        if (normalized.length() > MAX_GRADE_CODE_LENGTH) {
            throw new IllegalArgumentException("gradeCode must be at most " + MAX_GRADE_CODE_LENGTH + " characters");
        }
        return normalized;
    }

    public static String remarksOrNull(String remarks) {
        return Require.optionalText(remarks, "remarks", MAX_REMARKS_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public CourseEnrollment getCourseEnrollment() {
        return courseEnrollment;
    }

    public BigDecimal getMarksObtained() {
        return marksObtained;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public BigDecimal getInternalMarks() {
        return internalMarks;
    }

    public BigDecimal getInternalMax() {
        return internalMax;
    }

    public BigDecimal getExternalMarks() {
        return externalMarks;
    }

    public BigDecimal getExternalMax() {
        return externalMax;
    }

    public void recordSplit(BigDecimal internalMarks, BigDecimal internalMax, BigDecimal externalMarks, BigDecimal externalMax) {
        if (internalMarks == null && internalMax == null && externalMarks == null && externalMax == null) {
            this.internalMarks = null;
            this.internalMax = null;
            this.externalMarks = null;
            this.externalMax = null;
            return;
        }
        BigDecimal inMarks = validMarks(internalMarks, "internalMarks");
        BigDecimal inMax = validMarks(internalMax, "internalMax");
        BigDecimal exMarks = validMarks(externalMarks, "externalMarks");
        BigDecimal exMax = validMarks(externalMax, "externalMax");
        if (inMarks.compareTo(inMax) > 0 || exMarks.compareTo(exMax) > 0) {
            throw new IllegalArgumentException("A part's marks must not exceed its maximum");
        }
        this.internalMarks = inMarks;
        this.internalMax = inMax;
        this.externalMarks = exMarks;
        this.externalMax = exMax;
    }

    public String getGradeCode() {
        return gradeCode;
    }

    public String getRemarks() {
        return remarks;
    }

    public CourseResultStatus getStatus() {
        return status;
    }

    public FacultyProfile getRecordedByFacultyProfile() {
        return recordedByFacultyProfile;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void apply(FacultyProfile recordedBy, BigDecimal marksObtained, BigDecimal maxMarks, String gradeCode,
            String remarks) {
        FacultyProfile validRecorder = Require.notNull(recordedBy, "recordedByFacultyProfile");
        BigDecimal validMax = validMarks(maxMarks, "maxMarks");
        BigDecimal validMarks = validMarks(marksObtained, "marksObtained");
        if (validMax.signum() <= 0) {
            throw new IllegalArgumentException("maxMarks must be greater than zero");
        }
        if (validMarks.compareTo(validMax) > 0) {
            throw new IllegalArgumentException("marksObtained must not exceed maxMarks");
        }
        String validGrade = gradeCodeOrNull(gradeCode);
        String validRemarks = remarksOrNull(remarks);
        this.recordedByFacultyProfile = validRecorder;
        this.maxMarks = validMax;
        this.marksObtained = validMarks;
        this.gradeCode = validGrade;
        this.remarks = validRemarks;
    }

    private static BigDecimal validMarks(BigDecimal value, String name) {
        Require.notNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        if (value.stripTrailingZeros().scale() > MARK_DECIMAL_PLACES || value.compareTo(MAX_MARKS_LIMIT) > 0) {
            throw new IllegalArgumentException(name + " must be below 1,000,000 with at most 2 decimal places");
        }
        return value.setScale(MARK_DECIMAL_PLACES);
    }
}
