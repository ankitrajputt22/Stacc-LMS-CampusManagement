package com.stacc.backend.hostel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.approval.ApprovalCommands.CurrentApprovalStep;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestDetail;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelResponses.HostelAdminApplicationDetail;
import com.stacc.backend.hostel.HostelResponses.HostelAdminApplicationSummary;
import com.stacc.backend.hostel.HostelResponses.HostelAllocationPage;
import com.stacc.backend.hostel.HostelResponses.HostelAllocationResponse;
import com.stacc.backend.hostel.HostelResponses.HostelApplicationDetail;
import com.stacc.backend.hostel.HostelResponses.HostelApplicationPage;
import com.stacc.backend.hostel.HostelResponses.HostelApplicationSummary;
import com.stacc.backend.hostel.HostelResponses.StudentHostelAllocation;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading hostel applications and allocations, for students (their own) and admins (all). The approval status and
 * progress always come from the approval engine. It only reads.
 */
@Service
@Transactional(readOnly = true)
public class HostelQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final HostelApplicationRepository applications;
    private final HostelAllocationRepository allocations;
    private final StudentProfileRepository studentProfiles;
    private final ApprovalQueryService approvals;

    public HostelQueryService(HostelApplicationRepository applications, HostelAllocationRepository allocations,
            StudentProfileRepository studentProfiles, ApprovalQueryService approvals) {
        this.applications = applications;
        this.allocations = allocations;
        this.studentProfiles = studentProfiles;
        this.approvals = approvals;
    }

    /** The signed-in student's applications, newest first. */
    public List<HostelApplicationSummary> studentApplications(Long accountId) {
        Long profileId = studentProfiles.findByUserAccountId(accountId).map(profile -> profile.getId()).orElse(null);
        if (profileId == null) {
            return List.of();
        }
        List<HostelApplication> own = applications.findAllForStudent(profileId);
        Map<Long, List<HostelAllocation>> allocated = allocationsOf(own);
        return own.stream()
                .map(application -> new HostelApplicationSummary(application.getId(), application.getApplicationNumber(),
                        application.getAcademicSession().getId(), application.getAcademicSession().getCode(),
                        statusOf(application), application.getSubmittedAt(),
                        allocated.getOrDefault(application.getId(), List.of()).stream().anyMatch(HostelAllocation::isActive)))
                .toList();
    }

    /** One of the signed-in student's own applications. Another student's answers the same 404 as a missing one. */
    public HostelApplicationDetail studentApplication(Long accountId, Long applicationId) {
        HostelApplication application = loaded(applicationId);
        if (!application.getStudentProfile().getUserAccount().getId().equals(accountId)) {
            throw notFound();
        }
        List<HostelAllocation> stays = allocations.findAllForApplications(List.of(application.getId()));
        Optional<HostelAllocation> active = stays.stream().filter(HostelAllocation::isActive).findFirst();
        ApprovalRequestDetail approval = approvalOf(application);
        return new HostelApplicationDetail(application.getId(), application.getApplicationNumber(),
                application.getAcademicSession().getId(), application.getAcademicSession().getCode(), application.getNote(),
                application.getSubmittedAt(), statusOf(application),
                application.approvalStatus() == ApprovalRequestStatus.PENDING && active.isEmpty(),
                approval == null ? List.of() : approval.steps(),
                active.map(StudentHostelAllocation::of).orElse(null),
                stays.stream().filter(stay -> !stay.isActive()).map(StudentHostelAllocation::of).toList());
    }

    /** One page of every application, newest first, with each one's active allocation. */
    public HostelApplicationPage adminApplications(Long academicSessionId, ApprovalRequestStatus approvalStatus,
            String studentLoginId, Boolean hasActiveAllocation, int page, int size) {
        PageRequest request = pageOf(page, size);
        String loginId = studentLoginId == null || studentLoginId.isBlank() ? null : studentLoginId.strip();
        Page<HostelApplication> rows = applications.findQueue(academicSessionId, approvalStatus, loginId,
                hasActiveAllocation, request);
        Map<Long, List<HostelAllocation>> allocated = allocationsOf(rows.getContent());
        List<HostelAdminApplicationSummary> items = rows.getContent().stream()
                .map(application -> new HostelAdminApplicationSummary(application.getId(),
                        application.getApplicationNumber(), application.getStudentProfile().getUserAccount().getLoginId(),
                        application.getAcademicSession().getId(), application.getAcademicSession().getCode(),
                        statusOf(application), application.getSubmittedAt(),
                        allocated.getOrDefault(application.getId(), List.of()).stream().filter(HostelAllocation::isActive)
                                .findFirst().map(HostelAllocationResponse::of).orElse(null)))
                .toList();
        return new HostelApplicationPage(items, request.getPageNumber(), request.getPageSize(), rows.getTotalElements(),
                rows.getTotalPages());
    }

    /**
     * One application for the hostel office: approval progress and history from the approval engine, the step waiting
     * for a decision, whether this admin may decide it through the approval task routes, and every allocation.
     */
    public HostelAdminApplicationDetail adminApplication(ApprovalActor actor, Long applicationId) {
        HostelApplication application = loaded(applicationId);
        ApprovalRequestDetail approval = approvalOf(application);
        Long approvalRequestId = application.getApprovalRequest() == null ? null : application.getApprovalRequest().getId();
        CurrentApprovalStep current = approvals.currentStep(approvalRequestId).orElse(null);
        Long requesterId = application.getStudentProfile().getUserAccount().getId();
        boolean canDecide = current != null && actor.taskDecide() && actor.roleCodes().contains(current.requiredRoleCode())
                && !requesterId.equals(actor.accountId());
        List<HostelAllocation> stays = allocations.findAllForApplications(List.of(application.getId()));
        boolean canAllocate = application.approvalStatus() == ApprovalRequestStatus.APPROVED
                && stays.stream().noneMatch(HostelAllocation::isActive);
        return new HostelAdminApplicationDetail(application.getId(), application.getApplicationNumber(),
                application.getStudentProfile().getUserAccount().getLoginId(), application.getAcademicSession().getId(),
                application.getAcademicSession().getCode(), application.getNote(), application.getSubmittedAt(),
                statusOf(application), approval == null ? List.of() : approval.steps(),
                approval == null ? List.of() : approval.history(), current, canDecide, canAllocate,
                stays.stream().map(HostelAllocationResponse::of).toList());
    }

    /** One page of every allocation, newest first. */
    public HostelAllocationPage allocations(HostelAllocationStatus status, Long hostelId, Long academicSessionId,
            String studentLoginId, int page, int size) {
        PageRequest request = pageOf(page, size);
        String loginId = studentLoginId == null || studentLoginId.isBlank() ? null : studentLoginId.strip();
        Page<HostelAllocation> rows = allocations.findPage(status, hostelId, academicSessionId, loginId, request);
        return new HostelAllocationPage(rows.getContent().stream().map(HostelAllocationResponse::of).toList(),
                request.getPageNumber(), request.getPageSize(), rows.getTotalElements(), rows.getTotalPages());
    }

    private HostelApplication loaded(Long applicationId) {
        return (applicationId == null ? Optional.<HostelApplication>empty() : applications.findWithContextById(applicationId))
                .orElseThrow(HostelQueryService::notFound);
    }

    private ApprovalRequestDetail approvalOf(HostelApplication application) {
        return application.getApprovalRequest() == null ? null
                : approvals.requestDetail(application.getApprovalRequest().getId()).orElse(null);
    }

    private Map<Long, List<HostelAllocation>> allocationsOf(List<HostelApplication> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        return allocations.findAllForApplications(rows.stream().map(HostelApplication::getId).toList()).stream()
                .collect(Collectors.groupingBy(allocation -> allocation.getHostelApplication().getId()));
    }

    private static String statusOf(HostelApplication application) {
        ApprovalRequestStatus status = application.approvalStatus();
        return status == null ? null : status.name();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, HostelApplicationService.NOT_FOUND);
    }

    private static PageRequest pageOf(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The page must be 0 or more.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size);
    }
}
