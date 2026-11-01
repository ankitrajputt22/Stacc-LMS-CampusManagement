package com.stacc.backend.academic.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.academic.admin.AcademicSessionManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * One current academic session on a real database: the unique generated column refuses a second current session, and
 * making another session current moves the mark. Uses its own sessions and puts the demo session back afterwards.
 */
class CurrentSessionMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private AcademicSessionManagementService service;

    @Autowired
    private AcademicSessionRepository sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void onlyOneSessionIsEverCurrent() {
        Long demo = sessions.findByCurrentTrue().orElseThrow().getId();
        AcademicSession other = sessions.saveAndFlush(new AcademicSession("IT-CURRENT", LocalDate.of(2040, 7, 1),
                LocalDate.of(2041, 6, 30)));
        try {
            assertThatThrownBy(() -> jdbc.update("update academic_sessions set is_current = true where id = ?", other.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(service.makeCurrent(other.getId()).current()).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from academic_sessions where is_current", Integer.class)).isEqualTo(1);
            assertThat(sessions.findByCurrentTrue().orElseThrow().getId()).isEqualTo(other.getId());
        } finally {
            service.makeCurrent(demo);
        }
        assertThat(sessions.findByCurrentTrue().orElseThrow().getId()).isEqualTo(demo);
    }
}
