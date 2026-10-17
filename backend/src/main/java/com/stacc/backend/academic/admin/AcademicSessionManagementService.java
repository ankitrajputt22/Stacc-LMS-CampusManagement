package com.stacc.backend.academic.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.stacc.backend.academic.admin.AcademicMasterRequests.AcademicSessionCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.AcademicSessionUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.AcademicSessionResponse;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.academic.session.AcademicSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins list, create, and edit academic sessions. A session's code is set once; its dates and status can change. A
 * session moves forward from planned to active to closed, never back. Several sessions may be active at once, and
 * overlapping dates are allowed: no such rule has been decided. Nothing is ever deleted.
 */
@Service
@Transactional
public class AcademicSessionManagementService {

    static final String NOT_FOUND = "Academic session not found.";
    static final String CODE_TAKEN = "An academic session with this code already exists.";
    static final String DATES = "The end date must be after the start date.";
    static final String BACK_TO_PLANNED = "A session that has started or closed cannot go back to planned.";
    static final String REOPEN = "A closed academic session cannot be made active again.";

    private final AcademicSessionRepository sessions;

    public AcademicSessionManagementService(AcademicSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<AcademicSessionResponse> list() {
        return sessions.findAllByOrderByStartDateDescIdDesc().stream().map(AcademicSessionResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public AcademicSessionResponse get(Long academicSessionId) {
        return AcademicSessionResponse.of(find(academicSessionId));
    }

    public AcademicSessionResponse create(AcademicSessionCreate request) {
        requireOrdered(request.startDate(), request.endDate());
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (sessions.existsByCode(code)) {
            throw AcademicMasterErrors.conflict(CODE_TAKEN);
        }
        return AcademicSessionResponse.of(AcademicMasterErrors.savedOrConflict(
                () -> sessions.saveAndFlush(new AcademicSession(code, request.startDate(), request.endDate())), CODE_TAKEN));
    }

    public AcademicSessionResponse update(Long academicSessionId, AcademicSessionUpdate request) {
        AcademicSession session = find(academicSessionId);
        requireOrdered(request.startDate(), request.endDate());
        AcademicSessionStatus target = request.status();
        if (target != session.getStatus()) {
            if (target == AcademicSessionStatus.PLANNED) {
                throw AcademicMasterErrors.conflict(BACK_TO_PLANNED);
            }
            if (target == AcademicSessionStatus.ACTIVE && session.getStatus() == AcademicSessionStatus.CLOSED) {
                throw AcademicMasterErrors.conflict(REOPEN);
            }
        }
        session.reschedule(request.startDate(), request.endDate());
        if (target != session.getStatus()) {
            if (target == AcademicSessionStatus.ACTIVE) {
                session.markActive();
            } else {
                session.close();
            }
        }
        return AcademicSessionResponse.of(sessions.saveAndFlush(session));
    }

    private static void requireOrdered(LocalDate startDate, LocalDate endDate) {
        if (!endDate.isAfter(startDate)) {
            throw AcademicMasterErrors.badRequest(DATES);
        }
    }

    private AcademicSession find(Long academicSessionId) {
        return sessions.findById(academicSessionId).orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
    }
}
