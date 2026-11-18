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
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE);
        }
    }

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
