package com.stacc.backend.fees;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fees (v0.16.7), as records only. A student's fees are those of their program for the academic session they are
 * enrolled in now (else their latest one). What is due is worked out on every read: the structure's total, less
 * payments and approved scholarships, plus approved refunds. Asking for a refund locks the student's account row; a
 * decision locks the request row and then the account row, so a refund is never approved beyond what was paid over.
 */
@Service
@Transactional
public class FeeService {

    static final String NO_STUDENT = "No student profile is recorded for this account.";
    static final BigDecimal LIMIT = new BigDecimal("9999999999.99");

    public record Item(FeeStructureItem.Head head, BigDecimal amount) {
    }

    public record PaymentView(Long paymentId, String receiptNumber, BigDecimal amount, FeePayment.Mode mode, String reference,
            LocalDate paidOn, Instant recordedAt) {
    }

    public record AdjustmentView(Long adjustmentId, FeeAdjustment.Kind kind, String studentLoginId, String academicSessionCode,
            String title, BigDecimal amount, String reason, FeeAdjustment.Status status, String decisionComment, Instant createdAt,
            Instant decidedAt) {
    }

    /** A student's fees for one session. Due is negative when more was paid than owed; credit is then what may be refunded. */
    public record Ledger(String studentLoginId, String rollNumber, String programCode, String programName, String academicSessionCode,
            LocalDate dueDate, List<Item> items, BigDecimal total, BigDecimal paid, BigDecimal scholarships, BigDecimal refunded,
            BigDecimal due, BigDecimal credit, boolean overdue, List<PaymentView> payments, List<AdjustmentView> adjustments) {
    }

    public record Structure(Long programId, String programCode, String programName, Long academicSessionId, String academicSessionCode,
            LocalDate dueDate, List<Item> items, BigDecimal total) {
    }

    private final FeeStructureRepository structures;
    private final FeeStructureItemRepository items;
    private final FeePaymentRepository payments;
    private final FeeAdjustmentRepository adjustments;
    private final StudentProfileRepository students;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final ProgramRepository programs;
    private final AcademicSessionRepository sessions;
    private final UserAccountRepository accounts;
    private final Clock clock;
    private final EngagementProperties college;

    public FeeService(FeeStructureRepository structures, FeeStructureItemRepository items, FeePaymentRepository payments,
            FeeAdjustmentRepository adjustments, StudentProfileRepository students, SemesterEnrollmentRepository semesterEnrollments,
            ProgramRepository programs, AcademicSessionRepository sessions, UserAccountRepository accounts, Clock clock,
            EngagementProperties college) {
        this.structures = structures;
        this.items = items;
        this.payments = payments;
        this.adjustments = adjustments;
        this.students = students;
        this.semesterEnrollments = semesterEnrollments;
        this.programs = programs;
        this.sessions = sessions;
        this.accounts = accounts;
        this.clock = clock;
        this.college = college;
    }

    // --- Students

    @Transactional(readOnly = true)
    public Ledger mine(Long accountId) {
        return ledgerOf(studentOf(accountId));
    }

    public AdjustmentView applyForScholarship(Long accountId, String title, BigDecimal amount, String reason) {
        StudentProfile student = studentOf(accountId);
        AcademicSession session = sessionOf(student);
        return view(adjustments.saveAndFlush(new FeeAdjustment(FeeAdjustment.Kind.SCHOLARSHIP, student.getUserAccount(), session,
                text(title, 150, "Name the scholarship in at most 150 characters."), money(amount), text(reason, 1000,
                        "Give a reason of at most 1,000 characters."), clock.instant())));
    }

    /** Asks for money paid beyond what is owed to be returned; the amount may not exceed that credit. */
    public AdjustmentView askForRefund(Long accountId, BigDecimal amount, String reason) {
        accounts.findByIdForUpdate(accountId);
        StudentProfile student = studentOf(accountId);
        BigDecimal wanted = money(amount);
        if (wanted.compareTo(ledgerOf(student).credit()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "A refund can be at most what you paid beyond your fees.");
        }
        return view(adjustments.saveAndFlush(new FeeAdjustment(FeeAdjustment.Kind.REFUND, student.getUserAccount(), sessionOf(student),
                "Refund", wanted, text(reason, 1000, "Give a reason of at most 1,000 characters."), clock.instant())));
    }

    // --- The accounts office

    @Transactional(readOnly = true)
    public Ledger ledgerFor(String loginId) {
        UserAccount account = accounts.findByLoginId(loginId == null ? "" : loginId.strip())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No student with that college ID."));
        return ledgerOf(studentOf(account.getId()));
    }

    public PaymentView recordPayment(Long officeAccountId, String loginId, BigDecimal amount, FeePayment.Mode mode, String reference,
            LocalDate paidOn) {
        UserAccount account = accounts.findByLoginId(loginId == null ? "" : loginId.strip())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No student with that college ID."));
        accounts.findByIdForUpdate(account.getId());
        StudentProfile student = studentOf(account.getId());
        if (mode == null || paidOn == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose how and when it was paid.");
        }
        LocalDate today = LocalDate.now(clock.withZone(college.zone()));
        if (paidOn.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A payment cannot be dated in the future.");
        }
        String ref = reference == null || reference.isBlank() ? null : reference.strip();
        if (ref != null && ref.length() > 60) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A reference is at most 60 characters.");
        }
        return view(payments.saveAndFlush(new FeePayment(student.getUserAccount(), sessionOf(student), money(amount), mode, ref, paidOn,
                officeAccountId, clock.instant())));
    }

    @Transactional(readOnly = true)
    public List<AdjustmentView> queue(FeeAdjustment.Status status) {
        return adjustments.queue(status, PageRequest.of(0, 200)).stream().map(FeeService::view).toList();
    }

    /**
     * Approves or rejects a scholarship or refund once. The student's account row is locked first; an approved refund
     * must still be within the credit at that moment. A rejection needs a comment.
     */
    public AdjustmentView decide(Long officeAccountId, Long adjustmentId, boolean approve, String comment) {
        // Locking reads only until both locks are held, so the ledger below sees every decision committed before them.
        FeeAdjustment adjustment = (adjustmentId == null ? Optional.<FeeAdjustment>empty() : adjustments.findByIdForUpdate(adjustmentId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found."));
        accounts.findByIdForUpdate(adjustment.getStudent().getId());
        if (adjustment.getStatus() != FeeAdjustment.Status.SUBMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This request has already been decided.");
        }
        String note = comment == null || comment.isBlank() ? null : comment.strip();
        if (!approve && note == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Say why the request is rejected.");
        }
        if (note != null && note.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A comment is at most 1,000 characters.");
        }
        if (approve && adjustment.getKind() == FeeAdjustment.Kind.REFUND
                && adjustment.getAmount().compareTo(ledgerOf(studentOf(adjustment.getStudent().getId())).credit()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "The refund is more than the student has paid beyond their fees now.");
        }
        adjustment.decide(approve, officeAccountId, note, clock.instant());
        return view(adjustments.saveAndFlush(adjustment));
    }

    @Transactional(readOnly = true)
    public Structure structure(Long programId, Long sessionId) {
        Program program = programOf(programId);
        AcademicSession session = academicSessionOf(sessionId);
        return structures.findByProgramIdAndAcademicSessionId(programId, sessionId)
                .map(found -> structureView(found, items.findAllByFeeStructureIdOrderByIdAsc(found.getId())))
                .orElseGet(() -> new Structure(program.getId(), program.getCode(), program.getName(), session.getId(), session.getCode(),
                        null, List.of(), BigDecimal.ZERO));
    }

    /** Sets a program's fees for a session: each listed head added or changed, under the program row lock. */
    public Structure setStructure(Long programId, Long sessionId, LocalDate dueDate, List<Item> wanted) {
        Program program = programs.findByIdForUpdate(programId == null ? -1 : programId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Program not found."));
        AcademicSession session = academicSessionOf(sessionId);
        if (dueDate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the due date.");
        }
        Map<FeeStructureItem.Head, BigDecimal> heads = new EnumMap<>(FeeStructureItem.Head.class);
        for (Item item : wanted) {
            if (item.head() == null || heads.put(item.head(), moneyOrZero(item.amount())) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "List each fee head once.");
            }
        }
        Instant now = clock.instant();
        FeeStructure structure = structures.findByProgramIdAndAcademicSessionId(program.getId(), session.getId())
                .orElseGet(() -> structures.save(new FeeStructure(program, session, dueDate, now)));
        structure.changeDueDate(dueDate, now);
        Map<FeeStructureItem.Head, FeeStructureItem> existing = new EnumMap<>(FeeStructureItem.Head.class);
        items.findAllByFeeStructureIdOrderByIdAsc(structure.getId()).forEach(item -> existing.put(item.getHead(), item));
        heads.forEach((head, amount) -> {
            FeeStructureItem item = existing.get(head);
            if (item == null) {
                items.save(new FeeStructureItem(structure, head, amount));
            } else {
                item.changeAmount(amount);
            }
        });
        items.flush();
        return structureView(structures.saveAndFlush(structure), items.findAllByFeeStructureIdOrderByIdAsc(structure.getId()));
    }

    // --- Shared

    private Ledger ledgerOf(StudentProfile student) {
        AcademicSession session = sessionOf(student);
        Program program = student.getProgram();
        Long accountId = student.getUserAccount().getId();
        Optional<FeeStructure> structure = structures.findByProgramIdAndAcademicSessionId(program.getId(), session.getId());
        List<FeeStructureItem> heads = structure.map(found -> items.findAllByFeeStructureIdOrderByIdAsc(found.getId())).orElse(List.of());
        BigDecimal total = heads.stream().map(FeeStructureItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<FeePayment> paid = payments.findForStudent(accountId, session.getId());
        List<FeeAdjustment> asked = adjustments.findForStudent(accountId, session.getId());
        BigDecimal paidTotal = paid.stream().map(FeePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal scholarships = approved(asked, FeeAdjustment.Kind.SCHOLARSHIP);
        BigDecimal refunded = approved(asked, FeeAdjustment.Kind.REFUND);
        BigDecimal due = total.subtract(paidTotal).subtract(scholarships).add(refunded);
        LocalDate dueDate = structure.map(FeeStructure::getDueDate).orElse(null);
        boolean overdue = due.signum() > 0 && dueDate != null && LocalDate.now(clock.withZone(college.zone())).isAfter(dueDate);
        return new Ledger(student.getUserAccount().getLoginId(), student.getRollNumber(), program.getCode(), program.getName(),
                session.getCode(), dueDate, heads.stream().map(item -> new Item(item.getHead(), item.getAmount())).toList(), total,
                paidTotal, scholarships, refunded, due, due.signum() < 0 ? due.negate() : BigDecimal.ZERO, overdue,
                paid.stream().map(FeeService::view).toList(), asked.stream().map(FeeService::view).toList());
    }

    private static BigDecimal approved(List<FeeAdjustment> asked, FeeAdjustment.Kind kind) {
        return asked.stream().filter(adjustment -> adjustment.getKind() == kind && adjustment.getStatus() == FeeAdjustment.Status.APPROVED)
                .map(FeeAdjustment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private StudentProfile studentOf(Long accountId) {
        return students.findWithContextByUserAccountId(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NO_STUDENT));
    }

    // The session the student is enrolled in now, else their latest semester's session, else their admission session.
    private AcademicSession sessionOf(StudentProfile student) {
        List<SemesterEnrollment> semesters = semesterEnrollments.findAllWithContextByStudentProfileId(student.getId());
        return semesters.stream().filter(enrollment -> enrollment.getStatus() == SemesterEnrollmentStatus.ENROLLED).findFirst()
                .or(() -> semesters.stream().findFirst())
                .map(enrollment -> enrollment.getSemester().getAcademicSession())
                .orElse(student.getAdmissionSession());
    }

    private Program programOf(Long programId) {
        return (programId == null ? Optional.<Program>empty() : programs.findById(programId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Program not found."));
    }

    private AcademicSession academicSessionOf(Long sessionId) {
        return (sessionId == null ? Optional.<AcademicSession>empty() : sessions.findById(sessionId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Academic session not found."));
    }

    private static Structure structureView(FeeStructure structure, List<FeeStructureItem> heads) {
        Program program = structure.getProgram();
        AcademicSession session = structure.getAcademicSession();
        return new Structure(program.getId(), program.getCode(), program.getName(), session.getId(), session.getCode(),
                structure.getDueDate(), heads.stream().map(item -> new Item(item.getHead(), item.getAmount())).toList(),
                heads.stream().map(FeeStructureItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(LIMIT) > 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter an amount above zero with at most 2 decimal places.");
        }
        return amount.setScale(2);
    }

    private static BigDecimal moneyOrZero(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(LIMIT) > 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amounts are zero or more, with at most 2 decimal places.");
        }
        return amount.setScale(2);
    }

    private static String text(String value, int max, String message) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return text;
    }

    static PaymentView view(FeePayment payment) {
        return new PaymentView(payment.getId(), payment.receiptNumber(), payment.getAmount(), payment.getMode(), payment.getReference(),
                payment.getPaidOn(), payment.getCreatedAt());
    }

    static AdjustmentView view(FeeAdjustment adjustment) {
        return new AdjustmentView(adjustment.getId(), adjustment.getKind(), adjustment.getStudent().getLoginId(),
                adjustment.getAcademicSession().getCode(), adjustment.getTitle(), adjustment.getAmount(), adjustment.getReason(),
                adjustment.getStatus(), adjustment.getDecisionComment(), adjustment.getCreatedAt(), adjustment.getDecidedAt());
    }
}
