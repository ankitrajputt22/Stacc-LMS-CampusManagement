package com.stacc.backend.academic.admin;

import java.util.List;
import java.util.Locale;

import com.stacc.backend.academic.admin.AcademicMasterRequests.DepartmentCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.DepartmentUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.DepartmentResponse;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.department.DepartmentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins list, create, and edit departments. A department's code is set once; its name and status can change.
 * Nothing is ever deleted, and editing never touches the programs, courses, or people that refer to it.
 */
@Service
@Transactional
public class DepartmentManagementService {

    static final String NOT_FOUND = "Department not found.";
    static final String CODE_TAKEN = "A department with this code already exists.";
    static final String NAME_TAKEN = "A department with this name already exists.";
    static final String TAKEN = "A department with this code or name already exists.";

    private final DepartmentRepository departments;

    public DepartmentManagementService(DepartmentRepository departments) {
        this.departments = departments;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        return departments.findAllByOrderByCodeAscIdAsc().stream().map(DepartmentResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(Long departmentId) {
        return DepartmentResponse.of(find(departmentId));
    }

    public DepartmentResponse create(DepartmentCreate request) {
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        String name = request.name().strip();
        if (departments.existsByCode(code)) {
            throw AcademicMasterErrors.conflict(CODE_TAKEN);
        }
        if (departments.existsByName(name)) {
            throw AcademicMasterErrors.conflict(NAME_TAKEN);
        }
        return DepartmentResponse.of(AcademicMasterErrors.savedOrConflict(
                () -> departments.saveAndFlush(new Department(code, name)), TAKEN));
    }

    public DepartmentResponse update(Long departmentId, DepartmentUpdate request) {
        Department department = find(departmentId);
        String name = request.name().strip();
        if (departments.existsByNameAndIdNot(name, department.getId())) {
            throw AcademicMasterErrors.conflict(NAME_TAKEN);
        }
        department.rename(name);
        if (request.status() == DepartmentStatus.ACTIVE) {
            department.activate();
        } else {
            department.deactivate();
        }
        return DepartmentResponse.of(AcademicMasterErrors.savedOrConflict(() -> departments.saveAndFlush(department), TAKEN));
    }

    private Department find(Long departmentId) {
        return departments.findById(departmentId).orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
    }
}
