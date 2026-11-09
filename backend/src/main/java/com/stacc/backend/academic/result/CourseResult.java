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

/**
 * The official result of one course enrollment: the student, through that semester enrollment, in that exact
 * course offering. It is recorded explicitly by faculty and is never calculated from LMS grades, assignments, or
 * attendance. The student, course, offering, semester, programme, and session are reached through the enrollment
 * and are not stored again; there is at most one result per enrollment.
 *
 * <p>The grade code is optional official data, kept as entered: there is no grading scale, so it is never derived
 * from the marks. A result starts as a draft; once published it stays published, and a correction keeps its first
 * publication time. It is never deleted.
 */
@Entity
@Table(name = "course_results")
public class CourseResult {

    public static final int MARK_DECIMAL_PLACES = 2;
    /** The largest value the {@code DECIMAL(8,2)} columns hold. */
    public static final BigDecimal MAX_MARKS_LIMIT = new BigDecimal("999999.99");
    public static final int MAX_GRADE_CODE_LENGTH = 20;
    public static final int MAX_REMARKS_LENGTH = 2000;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    // No cascade on any link: the enrollment and the faculty profile are never created or deleted through a result.
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
        // Required by JPA.
    }

    /**
     * A new draft result. Whether the faculty member teaches the offering, and whether the enrollment may get a new
     * result, is checked by the service that records results, not here.
     */
    public CourseResult(CourseEnrollment courseEnrollment, FacultyProfile recordedBy, BigDecimal marksObtained,
            BigDecimal maxMarks, String gradeCode, String remarks) {
        this.courseEnrollment = Require.notNull(courseEnrollment, "courseEnrollment");
        apply(recordedBy, marksObtained, maxMarks, gradeCode, remarks);
    }

    /**
     * Records new values: an edit of a draft, or a correction of a published result, which stays published and keeps
     * its first publication time. The faculty profile becomes the one that recorded it last.
     */
    public void record(FacultyProfile recordedBy, BigDecimal marksObtained, BigDecimal maxMarks, String gradeCode,
            String remarks) {
        apply(recordedBy, marksObtained, maxMarks, gradeCode, remarks);
    }

    /** Publishes the result. Publishing again changes nothing, so the first publication time is kept. */
    public void publish(Instant at) {
        if (isPublished()) {
            return;
        }
        // The database keeps microseconds, so the answer shows the same moment as every later read.
        this.publishedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
        this.status = CourseResultStatus.PUBLISHED;
    }

    public boolean isPublished() {
        return status == CourseResultStatus.PUBLISHED;
    }

    /**
     * A grade code as it is kept: surrounding spaces removed and runs of spaces inside made single, so "B+" stays
     * "B+". Blank becomes none. Nothing is renamed or mapped to another grade.
     */
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

    /** Remarks as they are kept: plain text without surrounding spaces, line breaks kept. Blank becomes none. */
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
