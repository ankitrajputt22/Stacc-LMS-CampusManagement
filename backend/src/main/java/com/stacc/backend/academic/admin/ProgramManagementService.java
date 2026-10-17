package com.stacc.backend.academic.admin;

import java.util.List;
import java.util.Locale;

import com.stacc.backend.academic.admin.AcademicMasterRequests.ProgramCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.ProgramUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.ProgramResponse;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.program.ProgramStatus;
import com.stacc.backend.academic.semester.SemesterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins list, create, and edit programs. A program belongs to one department, loaded on the server from the ID sent;
 * its department and code are set once, and its name, duration, and status can change. Nothing is ever deleted.
 */
@Service
@Transactional
public class ProgramManagementService {

    static final String NOT_FOUND = "Program not found.";
    static final String DEPARTMENT_MISSING = "The selected department does not exist.";
    static final String CODE_TAKEN = "This department already has a program with this code.";
    static final String NAME_TAKEN = "This department already has a program with this name.";
    static final String TAKEN = "This department already has a program with this code or name.";

    private final ProgramRepository programs;
    private final DepartmentRepository departments;
    private final SemesterRepository semesters;

    public ProgramManagementService(ProgramRepository programs, DepartmentRepository departments, SemesterRepository semesters) {
        this.programs = programs;
        this.departments = departments;
        this.semesters = semesters;
    }

    @Transactional(readOnly = true)
    public List<ProgramResponse> list() {
        return programs.findAllWithDepartment().stream().map(ProgramResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public ProgramResponse get(Long programId) {
        return ProgramResponse.of(find(programId));
    }

    public ProgramResponse create(ProgramCreate request) {
        Department department = departments.findById(request.departmentId())
                .orElseThrow(() -> AcademicMasterErrors.badRequest(DEPARTMENT_MISSING));
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        String name = request.name().strip();
        if (programs.existsByDepartmentIdAndCode(department.getId(), code)) {
            throw AcademicMasterErrors.conflict(CODE_TAKEN);
        }
        if (programs.existsByDepartmentIdAndName(department.getId(), name)) {
            throw AcademicMasterErrors.conflict(NAME_TAKEN);
        }
        return ProgramResponse.of(AcademicMasterErrors.savedOrConflict(
                () -> programs.saveAndFlush(new Program(department, code, name, request.durationSemesters())), TAKEN));
    }

    /**
     * Changes the name, duration, and status. The duration cannot drop below the highest semester the program already
     * has, so no existing semester ends up beyond the program's length.
     */
    public ProgramResponse update(Long programId, ProgramUpdate request) {
        Program program = find(programId);
        String name = request.name().strip();
        if (programs.existsByDepartmentIdAndNameAndIdNot(program.getDepartment().getId(), name, program.getId())) {
            throw AcademicMasterErrors.conflict(NAME_TAKEN);
        }
        int highest = semesters.findMaxSemesterNumberByProgramId(program.getId()).orElse(0);
        if (request.durationSemesters() < highest) {
            throw AcademicMasterErrors.conflict("This program already has semester " + highest
                    + ", so its duration cannot be shorter than " + highest + " semesters.");
        }
        program.rename(name);
        program.changeDuration(request.durationSemesters());
        if (request.status() == ProgramStatus.ACTIVE) {
            program.activate();
        } else {
            program.deactivate();
        }
        return ProgramResponse.of(AcademicMasterErrors.savedOrConflict(() -> programs.saveAndFlush(program), TAKEN));
    }

    private Program find(Long programId) {
        return programs.findWithDepartmentById(programId).orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
    }
}
