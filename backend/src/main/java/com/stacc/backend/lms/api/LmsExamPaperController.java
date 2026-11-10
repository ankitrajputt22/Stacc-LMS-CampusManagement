package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.paper.ExamPaperDocument;
import com.stacc.backend.lms.paper.LmsExamBlueprint;
import com.stacc.backend.lms.paper.LmsExamBlueprintRow;
import com.stacc.backend.lms.paper.LmsExamPaper;
import com.stacc.backend.lms.paper.LmsExamPaperQuestion;
import com.stacc.backend.lms.paper.LmsExamPaperService;
import com.stacc.backend.lms.paper.LmsExamPaperService.PaperDetail;
import com.stacc.backend.lms.paper.LmsExamPaperService.PaperSummary;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionReviewStatus;
import com.stacc.backend.lms.question.QuestionStatus;
import com.stacc.backend.lms.question.QuestionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exam blueprints and papers of a course, for the faculty who teach it: planning, building, finalizing, and Word export. */
@RestController
@RequestMapping("/api/v1/lms/faculty")
@Tag(name = "LMS exam papers")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course, blueprint, or paper is not available to the account. All look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsExamPaperController {

    private static final String MANAGE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_EXAM_PAPER_MANAGE')";

    private final LmsExamPaperService service;

    public LmsExamPaperController(LmsExamPaperService service) {
        this.service = service;
    }

    // --- Requests

    public record BlueprintRowRequest(
            @Size(max = LmsExamBlueprintRow.MAX_SECTION_LENGTH) String sectionTitle,
            @Schema(description = "Only questions of this type, or any type when empty.") QuestionType questionType,
            QuestionDifficulty difficulty,
            BloomLevel bloomLevel,
            @Schema(description = "Only questions aligned to this outcome of the course.") Long outcomeId,
            @Size(max = LmsExamBlueprintRow.MAX_TOPIC_LENGTH) String topic,
            @Min(1) @Max(LmsExamBlueprintRow.MAX_COUNT) int questionCount,
            @NotNull @Schema(example = "5") BigDecimal marksEach) {

        LmsExamPaperService.RowInput input() {
            return new LmsExamPaperService.RowInput(sectionTitle, questionType, difficulty, bloomLevel, outcomeId, topic,
                    questionCount, marksEach);
        }
    }

    public record BlueprintRequest(
            @NotBlank @Size(max = LmsExamBlueprint.MAX_TITLE_LENGTH) String title,
            @NotNull @Size(min = 1, max = LmsExamBlueprint.MAX_ROWS) List<@NotNull @Valid BlueprintRowRequest> rows) {

        List<LmsExamPaperService.RowInput> inputs() {
            return rows.stream().map(BlueprintRowRequest::input).toList();
        }
    }

    public record PaperRequest(
            @Size(max = LmsExamPaper.MAX_TITLE_LENGTH) String title,
            @Size(max = LmsExamPaper.MAX_INSTRUCTIONS_LENGTH) String instructions,
            @Min(1) @Max(LmsExamPaper.MAX_DURATION_MINUTES) Integer durationMinutes) {
    }

    public record PaperQuestionRequest(
            @NotNull Long questionId,
            @Size(max = LmsExamBlueprintRow.MAX_SECTION_LENGTH) String sectionTitle,
            @NotNull BigDecimal marks) {
    }

    public record PaperQuestionsRequest(
            @NotNull @Size(max = LmsExamPaper.MAX_QUESTIONS) List<@NotNull @Valid PaperQuestionRequest> questions) {
    }

    // --- Responses

    public record BlueprintRowResponse(int position, String sectionTitle, QuestionType questionType, QuestionDifficulty difficulty,
            BloomLevel bloomLevel, Long outcomeId, String topic, int questionCount, BigDecimal marksEach) {

        static BlueprintRowResponse of(LmsExamBlueprintRow row) {
            return new BlueprintRowResponse(row.getPosition(), row.getSectionTitle(), row.getQuestionType(), row.getDifficulty(),
                    row.getBloomLevel(), row.getOutcomeId(), row.getTopic(), row.getQuestionCount(), row.getMarksEach());
        }
    }

    public record BlueprintResponse(Long id, String title, LmsExamBlueprint.Status status, List<BlueprintRowResponse> rows,
            int questionCount, BigDecimal totalMarks, Instant updatedAt) {

        static BlueprintResponse of(LmsExamBlueprint blueprint) {
            return new BlueprintResponse(blueprint.getId(), blueprint.getTitle(), blueprint.getStatus(),
                    blueprint.getRows().stream().map(BlueprintRowResponse::of).toList(), blueprint.questionCount(),
                    blueprint.totalMarks(), blueprint.getUpdatedAt());
        }
    }

    public record PaperSummaryResponse(Long id, String title, LmsExamPaper.Status status, Long blueprintId, Integer durationMinutes,
            long questionCount, BigDecimal totalMarks, Instant finalizedAt, Instant updatedAt) {

        static PaperSummaryResponse of(PaperSummary summary) {
            LmsExamPaper paper = summary.paper();
            return new PaperSummaryResponse(paper.getId(), paper.getTitle(), paper.getStatus(), paper.getBlueprintId(),
                    paper.getDurationMinutes(), summary.questionCount(), summary.totalMarks(), paper.getFinalizedAt(),
                    paper.getUpdatedAt());
        }
    }

    public record PaperQuestionResponse(
            @Schema(description = "The question's place in this paper.") Long id,
            Long questionId, int position, String sectionTitle, BigDecimal marks, QuestionType type, String text,
            LmsTextFormat textFormat, String explanation,
            @Schema(description = "Choices with the correct ones marked, or accepted short answers, in order.")
            List<LmsQuestionResponse.Option> options,
            Boolean trueFalseAnswer, String answerGuidance, String topic, QuestionDifficulty difficulty, BloomLevel bloomLevel,
            @Schema(description = "The aligned outcome's code, or null.", example = "CO2") String outcomeCode,
            QuestionReviewStatus reviewStatus, QuestionStatus status) {

        static PaperQuestionResponse of(LmsExamPaperQuestion link) {
            LmsQuestion question = link.getQuestion();
            return new PaperQuestionResponse(link.getId(), question.getId(), link.getPosition(), link.getSectionTitle(),
                    link.getMarks(), question.getType(), question.getText(), question.getTextFormat(), question.getExplanation(),
                    question.getOptions().stream().map(option -> new LmsQuestionResponse.Option(option.getText(), option.isCorrect())).toList(),
                    question.getTrueFalseAnswer(), question.getAnswerGuidance(), question.getTopic(), question.getDifficulty(),
                    question.getBloomLevel(), question.getOutcome() == null ? null : question.getOutcome().getCode(),
                    question.getReviewStatus(), question.getStatus());
        }
    }

    public record PaperResponse(Long id, String title, String instructions, Integer durationMinutes, LmsExamPaper.Status status,
            Long blueprintId, String courseCode, String courseName, BigDecimal totalMarks, Instant finalizedAt,
            Instant updatedAt, List<PaperQuestionResponse> questions) {

        static PaperResponse of(PaperDetail detail) {
            LmsExamPaper paper = detail.paper();
            return new PaperResponse(paper.getId(), paper.getTitle(), paper.getInstructions(), paper.getDurationMinutes(),
                    paper.getStatus(), paper.getBlueprintId(), detail.courseCode(), detail.courseName(), detail.totalMarks(),
                    paper.getFinalizedAt(), paper.getUpdatedAt(),
                    detail.questions().stream().map(PaperQuestionResponse::of).toList());
        }
    }

    // --- Blueprints

    @GetMapping("/courses/{lmsCourseId}/exam-blueprints")
    @PreAuthorize(MANAGE)
    @Operation(summary = "List a course's exam blueprints", description = "Every blueprint, archived ones included, newest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The blueprints. The list may be empty.")
    public List<BlueprintResponse> blueprints(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return service.blueprints(AuthenticatedAccount.idFrom(token), lmsCourseId).stream().map(BlueprintResponse::of).toList();
    }

    @PostMapping("/courses/{lmsCourseId}/exam-blueprints")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an exam blueprint",
            description = "Rows in order, each with a section, filters on the bank's questions, how many to take, and the marks each carries.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new blueprint.")
    @ApiResponse(responseCode = "400", description = "A value is missing or out of range.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "An outcome is not one of this course's.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public BlueprintResponse addBlueprint(@PathVariable Long lmsCourseId, @Valid @RequestBody BlueprintRequest request,
            @AuthenticationPrincipal Jwt token) {
        return BlueprintResponse.of(service.addBlueprint(AuthenticatedAccount.idFrom(token), lmsCourseId, request.title(), request.inputs()));
    }

    @PutMapping("/exam-blueprints/{blueprintId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change an exam blueprint", description = "Replaces the title and every row. Papers built earlier stay as they are.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The blueprint.")
    @ApiResponse(responseCode = "400", description = "A value is missing or out of range.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The blueprint is archived, or an outcome is not one of this course's.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public BlueprintResponse editBlueprint(@PathVariable Long blueprintId, @Valid @RequestBody BlueprintRequest request,
            @AuthenticationPrincipal Jwt token) {
        return BlueprintResponse.of(service.editBlueprint(AuthenticatedAccount.idFrom(token), blueprintId, request.title(), request.inputs()));
    }

    @PostMapping("/exam-blueprints/{blueprintId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Archive an exam blueprint", description = "It is kept but no longer changes or builds papers.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The blueprint.")
    public BlueprintResponse archiveBlueprint(@PathVariable Long blueprintId, @AuthenticationPrincipal Jwt token) {
        return BlueprintResponse.of(service.archiveBlueprint(AuthenticatedAccount.idFrom(token), blueprintId));
    }

    @PostMapping("/exam-blueprints/{blueprintId}/papers")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Build a paper from a blueprint",
            description = "A draft paper with, for each row, that many active, reviewed bank questions that match, chosen at "
                    + "random and never twice. Without a title it takes the blueprint's.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new paper.")
    @ApiResponse(responseCode = "409", description = "The blueprint is archived, or the bank has too few matching questions for a row (named).",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PaperResponse buildPaper(@PathVariable Long blueprintId, @Valid @RequestBody PaperRequest request,
            @AuthenticationPrincipal Jwt token) {
        Long userAccountId = AuthenticatedAccount.idFrom(token);
        Long paperId = service.buildPaper(userAccountId, blueprintId, request.title(), request.instructions(),
                request.durationMinutes()).getId();
        return PaperResponse.of(service.paper(userAccountId, paperId));
    }

    // --- Papers

    @GetMapping("/courses/{lmsCourseId}/exam-papers")
    @PreAuthorize(MANAGE)
    @Operation(summary = "List a course's exam papers", description = "Every paper, archived ones included, newest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The papers. The list may be empty.")
    public List<PaperSummaryResponse> papers(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return service.papers(AuthenticatedAccount.idFrom(token), lmsCourseId).stream().map(PaperSummaryResponse::of).toList();
    }

    @PostMapping("/courses/{lmsCourseId}/exam-papers")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an empty exam paper", description = "A draft without questions, to fill in the builder.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new paper.")
    @ApiResponse(responseCode = "400", description = "The title is missing or a value is out of range.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PaperResponse addPaper(@PathVariable Long lmsCourseId, @Valid @RequestBody PaperRequest request,
            @AuthenticationPrincipal Jwt token) {
        Long userAccountId = AuthenticatedAccount.idFrom(token);
        Long paperId = service.addPaper(userAccountId, lmsCourseId, request.title(), request.instructions(),
                request.durationMinutes()).getId();
        return PaperResponse.of(service.paper(userAccountId, paperId));
    }

    @GetMapping("/exam-papers/{paperId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Read an exam paper", description = "Its details and questions in order, with their answers.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The paper.")
    public PaperResponse paper(@PathVariable Long paperId, @AuthenticationPrincipal Jwt token) {
        return PaperResponse.of(service.paper(AuthenticatedAccount.idFrom(token), paperId));
    }

    @PutMapping("/exam-papers/{paperId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change a draft paper's details", description = "Title, plain-text instructions, and duration.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The paper.")
    @ApiResponse(responseCode = "400", description = "The title is missing or a value is out of range.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The paper is final or archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PaperResponse editPaper(@PathVariable Long paperId, @Valid @RequestBody PaperRequest request,
            @AuthenticationPrincipal Jwt token) {
        return PaperResponse.of(service.editPaper(AuthenticatedAccount.idFrom(token), paperId, request.title(),
                request.instructions(), request.durationMinutes()));
    }

    @PutMapping("/exam-papers/{paperId}/questions")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Save a draft paper's questions",
            description = "Replaces every question with this list, in its order, each with its section and marks.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The paper.")
    @ApiResponse(responseCode = "400", description = "A question is listed twice, too many are listed, or marks are out of range.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The paper is not a draft, or a question is not an active, reviewed question of this course.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PaperResponse setQuestions(@PathVariable Long paperId, @Valid @RequestBody PaperQuestionsRequest request,
            @AuthenticationPrincipal Jwt token) {
        List<LmsExamPaperService.PaperQuestionInput> inputs = request.questions().stream()
                .map(question -> new LmsExamPaperService.PaperQuestionInput(question.questionId(), question.sectionTitle(), question.marks()))
                .toList();
        return PaperResponse.of(service.setQuestions(AuthenticatedAccount.idFrom(token), paperId, inputs));
    }

    @PostMapping("/exam-papers/{paperId}/finalize")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Make a paper final",
            description = "Its details and questions no longer change, and its questions can no longer be edited in the bank.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The paper.")
    @ApiResponse(responseCode = "409", description = "The paper is archived, has no questions, or holds a question that is archived or needs review.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PaperResponse finalizePaper(@PathVariable Long paperId, @AuthenticationPrincipal Jwt token) {
        return PaperResponse.of(service.finalizePaper(AuthenticatedAccount.idFrom(token), paperId));
    }

    @PostMapping("/exam-papers/{paperId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Archive a paper", description = "It is kept for history and no longer changes.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The paper.")
    public PaperResponse archivePaper(@PathVariable Long paperId, @AuthenticationPrincipal Jwt token) {
        return PaperResponse.of(service.archivePaper(AuthenticatedAccount.idFrom(token), paperId));
    }

    @GetMapping("/exam-papers/{paperId}/document")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Download a paper as a Word document",
            description = "The paper as a .docx file, with an answer key on its own page when answers=true. Built per request.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The document, as an attachment.")
    public ResponseEntity<byte[]> document(@PathVariable Long paperId, @RequestParam(defaultValue = "false") boolean answers,
            @AuthenticationPrincipal Jwt token) {
        PaperDetail detail = service.paper(AuthenticatedAccount.idFrom(token), paperId);
        byte[] document = ExamPaperDocument.write(detail, answers);
        String name = "exam-paper-" + paperId + (answers ? "-with-answers" : "") + ".docx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ExamPaperDocument.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(document);
    }
}
