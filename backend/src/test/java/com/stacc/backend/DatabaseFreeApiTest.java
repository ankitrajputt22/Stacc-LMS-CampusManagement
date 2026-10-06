package com.stacc.backend;

import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.membership.LmsStudentMembershipRepository;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base for tests that call the API through the whole application without a database,
 * so they need no MySQL credentials. The account repository is replaced by a stand-in
 * that each test can prepare, and tokens are signed with a made-up test secret. Requests
 * pass through the Spring Security filter chain, as they do in the running app.
 *
 * <p>Without a database there are no real repositories. A service that needs repositories
 * can only start here if they are listed below as stand-ins, even when no test uses them.
 */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "stacc.jwt.secret=" + TestSecrets.JWT_SECRET
})
@MockitoBean(types = {
    CourseEnrollmentRepository.class,
    LmsCourseRepository.class,
    LmsStudentMembershipRepository.class
})
public abstract class DatabaseFreeApiTest {

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    @MockitoBean
    protected UserAccountRepository userAccountRepository;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }
}
