package com.stacc.backend.campus.services;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.campus.CampusActor;
import com.stacc.backend.campus.services.CampusServiceDefinition.Settings;
import com.stacc.backend.campus.services.CampusServiceInputs.CreateCampusServiceInput;
import com.stacc.backend.campus.services.CampusServiceInputs.UpdateCampusServiceInput;
import com.stacc.backend.campus.services.CampusServiceResponses.CampusServiceDefinitionResponse;
import com.stacc.backend.campus.services.CampusServiceResponses.CampusServiceSummary;
import com.stacc.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The campus service catalog: what requesters may ask for, and the admin's create, read, and update. There is no
 * delete: a service is made inactive instead. A service that needs approval names an approval workflow by its code,
 * which must exist and be active when the setting is made; the engine is asked, never copied.
 */
@Service
@Transactional
public class CampusServiceDefinitionService {

    static final String NOT_FOUND = "Campus service not found.";
    static final String DUPLICATE = "A campus service with this code already exists.";
    static final String WORKFLOW_UNAVAILABLE = "The approval workflow does not exist or is not active.";

    private final CampusServiceDefinitionRepository services;
    private final ApprovalQueryService approvals;

    public CampusServiceDefinitionService(CampusServiceDefinitionRepository services, ApprovalQueryService approvals) {
        this.services = services;
        this.approvals = approvals;
    }

    /**
     * The active services the actor may request, through any role it holds: STUDENT, FACULTY, or both. Each service
     * appears once. Empty for an account holding neither role.
     */
    @Transactional(readOnly = true)
    public List<CampusServiceSummary> available(CampusActor actor) {
        boolean student = actor.holds("STUDENT");
        boolean faculty = actor.holds("FACULTY");
        if (!student && !faculty) {
            return List.of();
        }
        return services.findAvailable(student, faculty).stream().map(CampusServiceSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public List<CampusServiceDefinitionResponse> list() {
        return services.findAllByOrderByCodeAscIdAsc().stream().map(CampusServiceDefinitionResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public CampusServiceDefinitionResponse get(Long serviceId) {
        return CampusServiceDefinitionResponse.of(loaded(serviceId));
    }

    public CampusServiceDefinitionResponse create(CreateCampusServiceInput input) {
        String code = valid(() -> CampusServiceDefinition.normalizedCode(input.code()));
        if (services.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE);
        }
        Settings settings = new Settings(input.name(), input.description(), input.active() == null || input.active(),
                Boolean.TRUE.equals(input.availableToStudent()), Boolean.TRUE.equals(input.availableToFaculty()),
                Boolean.TRUE.equals(input.approvalRequired()), input.approvalWorkflowCode());
        CampusServiceDefinition service = valid(() -> new CampusServiceDefinition(code, settings));
        requireStartableWorkflow(service);
        try {
            return CampusServiceDefinitionResponse.of(services.saveAndFlush(service));
        } catch (DataIntegrityViolationException clash) {
            // A unique key caught a race the check above could not see.
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE);
        }
    }

    /**
     * Replaces everything but the code. The workflow is checked when approval is switched on or the workflow changes;
     * an unchanged setting is kept even if its workflow was made inactive meanwhile, so the rest of the service can still
     * be edited. Existing requests keep their approval request either way.
     */
    public CampusServiceDefinitionResponse update(Long serviceId, UpdateCampusServiceInput input) {
        CampusServiceDefinition service = loaded(serviceId);
        boolean wasRequired = service.isApprovalRequired();
        String previousWorkflow = service.getApprovalWorkflowCode();
        Settings settings = new Settings(input.name(), input.description(), Boolean.TRUE.equals(input.active()),
                Boolean.TRUE.equals(input.availableToStudent()), Boolean.TRUE.equals(input.availableToFaculty()),
                Boolean.TRUE.equals(input.approvalRequired()), input.approvalWorkflowCode());
        valid(() -> {
            service.update(settings);
            return service;
        });
        if (service.isApprovalRequired()
                && (!wasRequired || !Objects.equals(previousWorkflow, service.getApprovalWorkflowCode()))) {
            requireStartableWorkflow(service);
        }
        return CampusServiceDefinitionResponse.of(services.saveAndFlush(service));
    }

    private void requireStartableWorkflow(CampusServiceDefinition service) {
        if (service.isApprovalRequired() && !approvals.isStartableWorkflow(service.getApprovalWorkflowCode())) {
            throw new ApiException(HttpStatus.CONFLICT, WORKFLOW_UNAVAILABLE);
        }
    }

    private CampusServiceDefinition loaded(Long serviceId) {
        return (serviceId == null ? Optional.<CampusServiceDefinition>empty() : services.findById(serviceId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private static <T> T valid(Supplier<T> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The service is not valid: " + invalid.getMessage() + ".");
        }
    }
}
