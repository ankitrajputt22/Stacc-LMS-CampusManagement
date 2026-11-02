package com.stacc.backend.academic.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.academic.admin.EnrollmentMonitorService.Filters;
import com.stacc.backend.academic.admin.EnrollmentMonitorService.MonitorPage;
import com.stacc.backend.academic.admin.EnrollmentMonitorService.MonitorRow;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The enrollment monitor's search query on MySQL over the demo data. It only reads. */
class EnrollmentMonitorMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private EnrollmentMonitorService monitor;

    @Test
    void filtersAndSearchTextNarrowTheEnrollments() {
        MonitorPage everything = monitor.search(new Filters(null, null, null, null, null, null, null), 0, 100);
        assertThat(everything.totalElements()).isGreaterThanOrEqualTo(9);

        MonitorPage byStudent = monitor.search(new Filters(null, null, null, null, null, null, "STUDENT2"), 0, 100);
        assertThat(byStudent.items()).isNotEmpty().allSatisfy(row -> assertThat(row.loginId()).isEqualTo("student2"));

        MonitorPage byName = monitor.search(new Filters(null, null, null, null, null, null, "aarav"), 0, 100);
        assertThat(byName.items()).isNotEmpty().allSatisfy(row -> assertThat(row.loginId()).isEqualTo("student1"));
        assertThat(byName.items()).extracting(MonitorRow::name).containsOnly("Aarav Sharma");

        MonitorPage withdrawn = monitor.search(
                new Filters(null, null, null, null, null, CourseEnrollmentStatus.WITHDRAWN, null), 0, 100);
        assertThat(withdrawn.items()).extracting(MonitorRow::courseCode).contains("CS304");

        MonitorPage byCode = monitor.search(new Filters(null, null, null, null, null, null, "cs201"), 0, 100);
        assertThat(byCode.items()).extracting(MonitorRow::courseCode).containsOnly("CS201");

        MonitorPage wildcard = monitor.search(new Filters(null, null, null, null, null, null, "%"), 0, 100);
        assertThat(wildcard.items()).isEmpty();

        MonitorPage firstTwo = monitor.search(new Filters(null, null, null, null, null, null, null), 0, 2);
        assertThat(firstTwo.items()).hasSize(2);
        assertThat(firstTwo.totalElements()).isEqualTo(everything.totalElements());
    }
}
