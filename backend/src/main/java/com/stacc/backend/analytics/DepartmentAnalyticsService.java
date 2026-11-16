package com.stacc.backend.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.responsibility.DepartmentHeadRepository;
import com.stacc.backend.academic.responsibility.DepartmentHeadResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Department analytics (v0.16.3) for heads of department. The departments come only from active head terms that
 * {@link DepartmentHeadResolver} counts for the signed-in account. Counts are live; attendance percentages follow
 * {@link AnalyticsRules} (groups of at least five); no student is named and nothing is ranked or stored.
 */
@Service
@Transactional(readOnly = true)
public class DepartmentAnalyticsService {

    public record ProgramLine(String code, String name, long students) {
    }

    public record OfferingLine(Long courseOfferingId, String courseCode, String courseName, String programCode, int semesterNumber, long enrolled,
            BigDecimal attendancePercent, long publishedResults) {
    }

    public record DepartmentView(Long departmentId, String code, String name, List<ProgramLine> programs, List<OfferingLine> offerings, String note) {
    }

    private final DepartmentHeadRepository heads;
    private final DepartmentAnalyticsQueries queries;

    public DepartmentAnalyticsService(DepartmentHeadRepository heads, DepartmentAnalyticsQueries queries) {
        this.heads = heads;
        this.queries = queries;
    }

    /** The departments the account heads now, each with its current-session figures; empty when it heads none. */
    public List<DepartmentView> forHead(Long accountId) {
        return heads.findAllActive().stream()
                .filter(head -> head.getUserAccount().getId().equals(accountId) && DepartmentHeadResolver.counts(head))
                .map(head -> head.getDepartment())
                .map(department -> {
                    List<DepartmentAnalyticsQueries.OfferingCount> offerings = queries.countOfferings(department.getId());
                    Map<Long, DepartmentAnalyticsQueries.AttendanceCount> attendance = offerings.isEmpty() ? Map.of()
                            : queries.countAttendance(offerings.stream().map(DepartmentAnalyticsQueries.OfferingCount::getOfferingId).toList()).stream()
                                    .collect(Collectors.toMap(DepartmentAnalyticsQueries.AttendanceCount::getOfferingId, Function.identity()));
                    return new DepartmentView(department.getId(), department.getCode(), department.getName(),
                            queries.countProgramStudents(department.getId()).stream()
                                    .map(count -> new ProgramLine(count.getCode(), count.getName(), count.getStudents())).toList(),
                            offerings.stream().map(count -> {
                                DepartmentAnalyticsQueries.AttendanceCount marked = attendance.get(count.getOfferingId());
                                return new OfferingLine(count.getOfferingId(), count.getCourseCode(), count.getCourseName(), count.getProgramCode(),
                                        count.getSemesterNumber(), count.getEnrolled(),
                                        marked == null ? null : AnalyticsRules.classPercent(marked.getPresent(), marked.getMarked(), count.getEnrolled()),
                                        count.getPublishedResults());
                            }).toList(),
                            AnalyticsRules.SMALL_GROUP);
                }).toList();
    }
}
