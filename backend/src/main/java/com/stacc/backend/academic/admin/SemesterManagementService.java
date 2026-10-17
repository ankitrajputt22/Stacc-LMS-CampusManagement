package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.AcademicMasterRequests.SemesterCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.SemesterUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.SemesterResponse;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.semester.SemesterStatus;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins list, create, and edit semesters. A semester is one numbered semester of one program in one academic
 * session, both loaded on the server from the IDs sent. Program, session, and number say what it is and never
 * change; only the status can, moving forward from planned to active to closed. Nothing is ever deleted.
 */
@Service
@Transactional
public class SemesterManagementService {

    static final String NOT_FOUND = "Semester not found.";
    static final String PROGRAM_MISSING = "The selected program does not exist.";
    static final String SESSION_MISSING = "The selected academic session does not exist.";
    static final String TAKEN = "This program already has this semester in this academic session.";
    static final String BACK_TO_PLANNED = "A semester that has started or closed cannot go back to planned.";
    static final String REOPEN = "A closed semester cannot be made active again.";

    private final SemesterRepository semesters;
    private final ProgramRepository programs;
    private final AcademicSessionRepository sessions;

    public SemesterManagementService(SemesterRepository semesters, ProgramRepository programs, AcademicSessionRepository sessions) {
        this.semesters = semesters;
        this.programs = programs;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<SemesterResponse> list() {
        return semesters.findAllWithContext().stream().map(SemesterResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public SemesterResponse get(Long semesterId) {
        return SemesterResponse.of(find(semesterId));
    }

    public SemesterResponse create(SemesterCreate request) {
        Program program = programs.findWithDepartmentById(request.programId())
                .orElseThrow(() -> AcademicMasterErrors.badRequest(PROGRAM_MISSING));
        AcademicSession session = sessions.findById(request.academicSessionId())
                .orElseThrow(() -> AcademicMasterErrors.badRequest(SESSION_MISSING));
        int number = request.semesterNumber();
        if (number > program.getDurationSemesters()) {
            throw AcademicMasterErrors.badRequest("Semester number must not be greater than the program's "
                    + program.getDurationSemesters() + " semesters.");
        }
        if (semesters.findByProgramIdAndAcademicSessionIdAndSemesterNumber(program.getId(), session.getId(), number).isPresent()) {
            throw AcademicMasterErrors.conflict(TAKEN);
        }
        return SemesterResponse.of(AcademicMasterErrors.savedOrConflict(
                () -> semesters.saveAndFlush(new Semester(program, session, number)), TAKEN));
    }

    public SemesterResponse update(Long semesterId, SemesterUpdate request) {
        Semester semester = find(semesterId);
        SemesterStatus target = request.status();
        if (target != semester.getStatus()) {
            if (target == SemesterStatus.PLANNED) {
                throw AcademicMasterErrors.conflict(BACK_TO_PLANNED);
            }
            if (target == SemesterStatus.ACTIVE && semester.getStatus() == SemesterStatus.CLOSED) {
                throw AcademicMasterErrors.conflict(REOPEN);
            }
            if (target == SemesterStatus.ACTIVE) {
                semester.markActive();
            } else {
                semester.close();
            }
        }
        return SemesterResponse.of(semesters.saveAndFlush(semester));
    }

    private Semester find(Long semesterId) {
        return semesters.findWithContextById(semesterId).orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
    }
}
