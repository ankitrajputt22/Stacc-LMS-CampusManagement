package com.stacc.backend.report.export;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.stacc.backend.analytics.AnalyticsService;
import com.stacc.backend.analytics.AnalyticsService.CourseAnalytics;
import com.stacc.backend.audit.AuditAction;
import com.stacc.backend.audit.AuditService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.report.AdminReportQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background exports: a request is recorded (and audited) and runs after it commits on the application's task executor,
 * writing an XLSX file to private storage. Only the person who asked can download it, until it expires after a day;
 * each download is audited. Expired files are removed when new exports are requested. The content uses exactly the
 * screens' own rules: the enrollment report's query, and course analytics with the faculty access check of the asker.
 */
@Service
public class ReportExportService {

    static final Duration LIFETIME = Duration.ofHours(24);
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportExportService.class);

    public record ExportView(Long exportId, ReportExport.Kind kind, Long parameter, String status, String fileName, Long sizeBytes,
            String failure, Instant createdAt, Instant expiresAt) {
    }

    public record Download(String fileName, long sizeBytes, String contentType, InputStream content) {
    }

    private final ReportExportRepository exports;
    private final ObjectStorageService storage;
    private final AdminReportQueryService adminReports;
    private final AnalyticsService analytics;
    private final AuditService audit;
    private final org.springframework.beans.factory.ObjectProvider<TransactionTemplate> transactionTemplates;
    private final Executor executor;
    private final Clock clock;

    public ReportExportService(ReportExportRepository exports, ObjectStorageService storage, AdminReportQueryService adminReports,
            AnalyticsService analytics, AuditService audit, org.springframework.beans.factory.ObjectProvider<TransactionTemplate> transactionTemplates,
            @Qualifier("applicationTaskExecutor") Executor executor, Clock clock) {
        this.exports = exports;
        this.storage = storage;
        this.adminReports = adminReports;
        this.analytics = analytics;
        this.audit = audit;
        this.transactionTemplates = transactionTemplates;
        this.executor = executor;
        this.clock = clock;
    }

    /** Records an export for the account (whose access the controller already checked) and starts it after commit. */
    public ExportView request(Long accountId, ReportExport.Kind kind, Long parameter) {
        if (kind == ReportExport.Kind.COURSE_ANALYTICS) {
            // Fails now, with the same 404, if the course is not one the asker can open.
            analytics.forCourse(accountId, parameter);
        }
        ReportExport export = transactions().execute(status -> {
            cleanUpExpired();
            Instant now = now();
            ReportExport saved = exports.saveAndFlush(new ReportExport(accountId, kind, parameter, now, now.plus(LIFETIME)));
            audit.record(accountId, AuditAction.REPORT_EXPORT_REQUESTED, "REPORT_EXPORT", saved.getId(), kind.name());
            Long id = saved.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(() -> run(id));
                }
            });
            return saved;
        });
        return view(export);
    }

    public List<ExportView> mine(Long accountId) {
        return exports.findRecent(accountId, PageRequest.of(0, 20)).stream().map(this::view).toList();
    }

    public Download download(Long accountId, Long exportId) {
        ReportExport export = exports.findById(exportId == null ? -1L : exportId).filter(e -> e.getAccountId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Export not found."));
        if (export.getStatus() != ReportExport.Status.READY || now().isAfter(export.getExpiresAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "This export is not ready or has expired. Request a new one.");
        }
        transactions().executeWithoutResult(status ->
                audit.record(accountId, AuditAction.REPORT_EXPORT_DOWNLOADED, "REPORT_EXPORT", export.getId(), export.getKind().name()));
        return new Download(export.getFileName(), export.getSizeBytes(), XLSX, storage.open(export.getObjectKey()));
    }

    void run(Long exportId) {
        ReportExport export = transactions().execute(status -> {
            ReportExport found = exports.findById(exportId).orElseThrow();
            found.running();
            return exports.saveAndFlush(found);
        });
        try {
            byte[] content = transactions().execute(status -> content(export));
            String key = "reports/exports/" + export.getId() + "/" + UUID.randomUUID();
            storage.store(key, new ByteArrayInputStream(content), content.length, XLSX);
            String fileName = export.getKind().name().toLowerCase().replace('_', '-') + "-" + export.getId() + ".xlsx";
            transactions().executeWithoutResult(status -> {
                ReportExport found = exports.findById(exportId).orElseThrow();
                found.ready(key, fileName, content.length, now());
                exports.saveAndFlush(found);
            });
        } catch (RuntimeException failure) {
            LOGGER.warn("A report export failed", failure);
            String reason = failure instanceof ApiException api && api.getStatus().is4xxClientError() ? api.getMessage()
                    : "The export could not be made. Try again later.";
            transactions().executeWithoutResult(status -> {
                ReportExport found = exports.findById(exportId).orElseThrow();
                found.failed(reason, now());
                exports.saveAndFlush(found);
            });
        }
    }

    private byte[] content(ReportExport export) {
        return switch (export.getKind()) {
            case ADMIN_ENROLLMENTS -> XlsxWriter.write(List.of(new XlsxWriter.Sheet("Enrollments", adminReports.enrollmentHeader(),
                    adminReports.enrollmentRows(adminReports.filters(null, null, null)))));
            case COURSE_ANALYTICS -> courseAnalytics(analytics.forCourse(export.getAccountId(), export.getParameter()));
        };
    }

    private static byte[] courseAnalytics(CourseAnalytics a) {
        return XlsxWriter.write(List.of(
                new XlsxWriter.Sheet("Summary", List.of("Enrolled", "Attendance %", "Note"),
                        List.of(List.of(String.valueOf(a.enrolled()), text(a.attendancePercent()), a.note() == null ? "" : a.note()))),
                new XlsxWriter.Sheet("Assignments", List.of("Assignment", "Due", "Submitted", "Submitted %", "Late", "Graded", "Average %"),
                        a.assignments().stream().map(l -> List.of(l.title(), String.valueOf(l.dueAt()), String.valueOf(l.submitted()),
                                text(l.submittedPercent()), String.valueOf(l.late()), String.valueOf(l.graded()), text(l.averagePercent()))).toList()),
                new XlsxWriter.Sheet("Quizzes", List.of("Quiz", "Handed in", "Average %"),
                        a.quizzes().stream().map(q -> List.of(q.title(), String.valueOf(q.handedIn()), text(q.averagePercent()))).toList()),
                new XlsxWriter.Sheet("Required pages", List.of("Page", "Completed", "Completed %"),
                        a.requiredPages().stream().map(p -> List.of(p.title(), String.valueOf(p.completed()), text(p.completedPercent()))).toList())));
    }

    private static String text(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private void cleanUpExpired() {
        for (ReportExport expired : exports.findExpired(now(), PageRequest.of(0, 50))) {
            try {
                if (expired.getObjectKey() != null) {
                    storage.delete(expired.getObjectKey());
                }
                expired.expired();
            } catch (RuntimeException failure) {
                LOGGER.warn("An expired export's file could not be removed", failure);
            }
        }
    }

    private ExportView view(ReportExport e) {
        String status = e.getStatus() == ReportExport.Status.READY && now().isAfter(e.getExpiresAt()) ? "EXPIRED" : e.getStatus().name();
        return new ExportView(e.getId(), e.getKind(), e.getParameter(), status, e.getFileName(), e.getSizeBytes(), e.getFailure(),
                e.getCreatedAt(), e.getExpiresAt());
    }

    private TransactionTemplate transactions() {
        return transactionTemplates.getObject();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
