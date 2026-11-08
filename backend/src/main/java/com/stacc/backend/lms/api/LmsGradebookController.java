package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.gradebook.LmsGradeEntry;
import com.stacc.backend.lms.gradebook.LmsGradeItem;
import com.stacc.backend.lms.gradebook.LmsGradebookService;
import com.stacc.backend.lms.gradebook.LmsStudentGradesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The course gradebook for faculty, and each student's own published grades. */
@RestController
@RequestMapping("/api/v1/lms")
@Tag(name = "LMS gradebook")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course or item is not available to the account. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsGradebookController {

    private static final String MANAGE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_GRADEBOOK_MANAGE')";
    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW') and hasAuthority('LMS_GRADE_VIEW')";

    private final LmsGradebookService gradebook;
    private final LmsStudentGradesService studentGrades;

    public LmsGradebookController(LmsGradebookService gradebook, LmsStudentGradesService studentGrades) {
        this.gradebook = gradebook;
        this.studentGrades = studentGrades;
    }

    /** A manual item. */
    public record ItemResponse(Long id, String title, BigDecimal maxMarks, Instant publishedAt) {
        static ItemResponse of(LmsGradeItem item) {
            return new ItemResponse(item.getId(), item.getTitle(), item.getMaxMarks(), item.getPublishedAt());
        }
    }

    /** A student's marks for a manual item. */
    public record EntryResponse(Long courseEnrollmentId, BigDecimal marks, String feedback, Instant gradedAt) {
        static EntryResponse of(LmsGradeEntry entry) {
            return new EntryResponse(entry.getCourseEnrollment().getId(), entry.getMarks(), entry.getFeedback(), entry.getGradedAt());
        }
    }

    @GetMapping("/faculty/courses/{lmsCourseId}/gradebook")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Read a course's gradebook",
            description = "Columns for published assignments, published graded quizzes, and manual items, with every current student's marks.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The gradebook.")
    public LmsGradebookResponse gradebook(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return LmsGradebookResponse.of(gradebook.gradebook(AuthenticatedAccount.idFrom(token), lmsCourseId));
    }

    @PostMapping("/faculty/courses/{lmsCourseId}/gradebook/items")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a manual item", description = "Its grades are not shown to students until it is published.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The item.")
    public ItemResponse addItem(@PathVariable Long lmsCourseId, @Valid @RequestBody LmsGradeItemRequest request,
            @AuthenticationPrincipal Jwt token) {
        return ItemResponse.of(gradebook.addManualItem(AuthenticatedAccount.idFrom(token), lmsCourseId, request.title(), request.maxMarks()));
    }

    @PutMapping("/faculty/gradebook/items/{itemId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change a manual item", description = "The maximum cannot fall below marks already entered.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The item.")
    @ApiResponse(responseCode = "409", description = "Marks already entered are above the new maximum.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ItemResponse editItem(@PathVariable Long itemId, @Valid @RequestBody LmsGradeItemRequest request,
            @AuthenticationPrincipal Jwt token) {
        return ItemResponse.of(gradebook.editManualItem(AuthenticatedAccount.idFrom(token), itemId, request.title(), request.maxMarks()));
    }

    @PostMapping("/faculty/courses/{lmsCourseId}/gradebook/publish")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Publish a column's grades",
            description = "Students see the column on their Grades page from now on. Publishing again changes nothing; nothing unpublishes.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The column's record.")
    @ApiResponse(responseCode = "409", description = "Not a published assignment, published graded quiz, or manual item of this course.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsGradebookResponse.Column publish(@PathVariable Long lmsCourseId, @Valid @RequestBody LmsGradebookPublishRequest request,
            @AuthenticationPrincipal Jwt token) {
        LmsGradeItem item = gradebook.publishColumn(AuthenticatedAccount.idFrom(token), lmsCourseId, request.type(), request.sourceId());
        return new LmsGradebookResponse.Column(request.type(), request.sourceId(), item.getTitle(), item.getMaxMarks(), null, true,
                item.getPublishedAt());
    }

    @PutMapping("/faculty/gradebook/items/{itemId}/entries/{courseEnrollmentId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Enter a student's marks for a manual item", description = "From 0 to the item's maximum, replacing earlier marks.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The marks.")
    @ApiResponse(responseCode = "400", description = "The marks are out of range.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public EntryResponse enterMarks(@PathVariable Long itemId, @PathVariable Long courseEnrollmentId,
            @Valid @RequestBody LmsGradeEntryRequest request, @AuthenticationPrincipal Jwt token) {
        return EntryResponse.of(gradebook.enterMarks(AuthenticatedAccount.idFrom(token), itemId, courseEnrollmentId, request.marks(),
                request.feedback()));
    }

    @GetMapping("/courses/{lmsCourseId}/grades")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Read my grades in a course",
            description = "Only published columns, and for quizzes only results already released. Never anyone else's marks.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The grades.")
    public LmsStudentGradesResponse grades(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return LmsStudentGradesResponse.of(studentGrades.grades(AuthenticatedAccount.idFrom(token), lmsCourseId));
    }
}
