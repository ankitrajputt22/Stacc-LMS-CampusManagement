package com.stacc.backend.academic.exam.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.stacc.backend.academic.exam.ExamService;
import com.stacc.backend.academic.exam.ExamService.MyExam;
import com.stacc.backend.academic.exam.ExamService.SeatPlan;
import com.stacc.backend.academic.exam.ExamService.SessionView;
import com.stacc.backend.academic.exam.ExamService.SlotView;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Examinations: a student's own exams with seats, and the admin routes that build and publish exam sessions. */
@RestController
@Tag(name = "Examinations")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "No such semester, session, offering, or exam.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class ExamController {

    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('EXAM_SCHEDULE_VIEW_OWN')";
    private static final String ADMIN = "hasRole('ADMIN') and hasAuthority('EXAM_MANAGE')";

    private final ExamService service;

    public ExamController(ExamService service) {
        this.service = service;
    }

    public record SessionRequest(@NotNull Long semesterId, @NotNull @Size(max = 150) String name) {
    }

    public record ExamRequest(@NotNull Long courseOfferingId, @NotNull LocalDate examDate, @NotNull LocalTime startsAt,
            @NotNull LocalTime endsAt, @NotNull @Size(max = 40) String hall, @NotNull Integer hallSeats) {
    }

    @GetMapping("/api/v1/erp/exams/me")
    @PreAuthorize(STUDENT)
    @Operation(summary = "The signed-in student's exams",
            description = "Published exams of the offerings the student is enrolled in now, earliest first, with the seat once given.")
    public List<MyExam> mine(@AuthenticationPrincipal Jwt accessToken) {
        return service.mine(AuthenticatedAccount.idFrom(accessToken));
    }

    @GetMapping("/api/v1/admin/erp/exams")
    @PreAuthorize(ADMIN)
    @Operation(summary = "A semester's exam sessions", description = "Each with its exams, seats given, and students enrolled.")
    public List<SessionView> sessions(@RequestParam Long semesterId) {
        return service.forSemester(semesterId);
    }

    @PostMapping("/api/v1/admin/erp/exams")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start an exam session as a draft")
    public SessionView create(@Valid @RequestBody SessionRequest request) {
        return service.createSession(request.semesterId(), request.name());
    }

    @PostMapping("/api/v1/admin/erp/exams/{examSessionId}/exams")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an exam", description = "One exam per offering of the session's semester (409 for a second).")
    public SlotView addExam(@PathVariable Long examSessionId, @Valid @RequestBody ExamRequest request) {
        return service.addExam(examSessionId, request.courseOfferingId(), request.examDate(), request.startsAt(), request.endsAt(),
                request.hall(), request.hallSeats());
    }

    @PostMapping("/api/v1/admin/erp/exams/slots/{slotId}/remove")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an exam", description = "Kept as history with its seats.")
    public void remove(@PathVariable Long slotId) {
        service.removeExam(slotId);
    }

    @PostMapping("/api/v1/admin/erp/exams/slots/{slotId}/seat-plan")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Generate the seat plan", description = "Seats every enrolled student without one, in college ID order; 409 past the hall's seats.")
    public SeatPlan seatPlan(@PathVariable Long slotId) {
        return service.seatPlan(slotId);
    }

    @PostMapping("/api/v1/admin/erp/exams/{examSessionId}/publish")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Publish an exam session", description = "Students then see its exams; publishing again changes nothing.")
    public SessionView publish(@PathVariable Long examSessionId) {
        return service.publish(examSessionId);
    }
}
