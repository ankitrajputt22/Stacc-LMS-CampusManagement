package com.stacc.backend.academic.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.academic.admin.AcademicMasterRequests.AcademicSessionCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.CourseCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.DepartmentUpdate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.ProgramCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.SemesterCreate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.CourseResponse;
import com.stacc.backend.academic.admin.AcademicMasterResponses.DepartmentResponse;
import com.stacc.backend.academic.department.DepartmentStatus;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls the admin academic-master routes through Spring Security. The services are stand-ins, so these tests are
 * about who gets in, what reaches the services, and what is sent back.
 */
class AdminAcademicControllerTest extends DatabaseFreeApiTest {

    private static final String ROOT = "/api/admin/academic/";
    private static final List<String> MASTERS = List.of("departments", "programs", "sessions", "semesters", "courses");

    private static final String DEPARTMENT = "{\"code\":\"CSE\",\"name\":\"Computer Science and Engineering\"}";
    private static final String PROGRAM = "{\"departmentId\":3,\"code\":\"BTECH-CSE\",\"name\":\"B.Tech. CSE\",\"durationSemesters\":8}";
    private static final String SESSION = "{\"code\":\"2026-27\",\"startDate\":\"2026-07-01\",\"endDate\":\"2027-06-30\"}";
    private static final String SEMESTER = "{\"programId\":4,\"academicSessionId\":2,\"semesterNumber\":3}";
    private static final String COURSE = "{\"departmentId\":3,\"code\":\"CS301\",\"name\":\"Data Structures\",\"credits\":4}";

    @MockitoBean
    private DepartmentManagementService departments;

    @MockitoBean
    private ProgramManagementService programs;

    @MockitoBean
    private AcademicSessionManagementService sessions;

    @MockitoBean
    private SemesterManagementService semesters;

    @MockitoBean
    private CourseManagementService courses;

    private static String token(String... authorities) {
        return TestTokens.valid("ADM-TEST-1", 1, authorities);
    }

    private static String viewer() {
        return token("ROLE_ADMIN", "ACADEMIC_MASTER_VIEW");
    }

    private static String manager() {
        return token("ROLE_ADMIN", "ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String createBody(String master) {
        return switch (master) {
            case "departments" -> DEPARTMENT;
            case "programs" -> PROGRAM;
            case "sessions" -> SESSION;
            case "semesters" -> SEMESTER;
            default -> COURSE;
        };
    }

    private static String updateBody(String master) {
        return switch (master) {
            case "departments" -> "{\"name\":\"Computing\",\"status\":\"ACTIVE\"}";
            case "programs" -> "{\"name\":\"B.Tech.\",\"durationSemesters\":8,\"status\":\"ACTIVE\"}";
            case "sessions" -> "{\"startDate\":\"2026-07-01\",\"endDate\":\"2027-06-30\",\"status\":\"ACTIVE\"}";
            case "semesters" -> "{\"status\":\"ACTIVE\"}";
            default -> "{\"name\":\"Data Structures\",\"credits\":4,\"status\":\"ACTIVE\"}";
        };
    }

    private static List<MockHttpServletRequestBuilder> reads(String master) {
        return List.of(get(ROOT + master), get(ROOT + master + "/1"));
    }

    private static List<MockHttpServletRequestBuilder> changes(String master) {
        return List.of(json(post(ROOT + master), createBody(master)), json(put(ROOT + master + "/1"), updateBody(master)));
    }

    private void verifyNoServiceCalled() {
        verifyNoInteractions(departments, programs, sessions, semesters, courses);
    }

    @Test
    void anAdminWithTheViewPermissionReadsEveryMaster() throws Exception {
        when(departments.list()).thenReturn(List.of(new DepartmentResponse(3L, "CSE", "Computer Science and Engineering", "ACTIVE")));
        when(courses.get(12L)).thenReturn(new CourseResponse(12L, 3L, "CSE", "Computer Science and Engineering", "CS301",
                "Data Structures", new BigDecimal("4.00"), "ACTIVE"));

        mockMvc.perform(as(viewer(), get(ROOT + "departments")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CSE"))
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());
        mockMvc.perform(as(viewer(), get(ROOT + "courses/12")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credits").value(4.0))
                .andExpect(jsonPath("$.departmentCode").value("CSE"))
                .andExpect(jsonPath("$.semesterId").doesNotExist());
        for (String master : MASTERS) {
            mockMvc.perform(as(viewer(), get(ROOT + master))).andExpect(status().isOk());
        }
    }

    @Test
    void readingNeedsTheAdminRoleAndTheViewPermissionTogether() throws Exception {
        for (String denied : new String[] {
                token("ROLE_ADMIN", "ACADEMIC_MASTER_MANAGE"),
                token("ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE"),
                token("ROLE_STUDENT", "ACADEMIC_MASTER_VIEW"),
                token("ROLE_FACULTY", "ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE", "ATTENDANCE_MANAGE")}) {
            for (String master : MASTERS) {
                for (MockHttpServletRequestBuilder read : reads(master)) {
                    mockMvc.perform(as(denied, read)).andExpect(status().isForbidden());
                }
            }
        }
        for (String master : MASTERS) {
            for (MockHttpServletRequestBuilder read : reads(master)) {
                mockMvc.perform(read).andExpect(status().isUnauthorized());
            }
        }
        verifyNoServiceCalled();
    }

    @Test
    void changingNeedsTheManagePermissionToo() throws Exception {
        for (String denied : new String[] {
                viewer(),
                token("ROLE_ADMIN", "ACADEMIC_MASTER_MANAGE"),
                token("ROLE_STUDENT", "ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE"),
                token("ROLE_FACULTY", "ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE")}) {
            for (String master : MASTERS) {
                for (MockHttpServletRequestBuilder change : changes(master)) {
                    mockMvc.perform(as(denied, change)).andExpect(status().isForbidden());
                }
            }
        }
        for (String master : MASTERS) {
            for (MockHttpServletRequestBuilder change : changes(master)) {
                mockMvc.perform(change).andExpect(status().isUnauthorized());
            }
        }
        verifyNoServiceCalled();
    }

    @Test
    void aNonAdminIsRefusedBeforeTheRequestBodyIsValidated() throws Exception {
        for (String denied : new String[] {
                token("ROLE_STUDENT", "ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE"),
                token("ROLE_FACULTY", "ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE")}) {
            for (String master : MASTERS) {
                mockMvc.perform(as(denied, json(post(ROOT + master), "{}"))).andExpect(status().isForbidden());
                mockMvc.perform(as(denied, json(put(ROOT + master + "/1"), "not json"))).andExpect(status().isForbidden());
            }
        }
        mockMvc.perform(json(post(ROOT + "departments"), "{}")).andExpect(status().isUnauthorized());
        verifyNoServiceCalled();
    }

    @Test
    void creatingAnswers201AndSendsOnlyTheRequestFields() throws Exception {
        for (String master : MASTERS) {
            mockMvc.perform(as(manager(), json(post(ROOT + master), createBody(master)))).andExpect(status().isCreated());
        }
        verify(programs).create(new ProgramCreate(3L, "BTECH-CSE", "B.Tech. CSE", 8));
        verify(sessions).create(new AcademicSessionCreate("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)));
        verify(semesters).create(new SemesterCreate(4L, 2L, 3));
        verify(courses).create(new CourseCreate(3L, "CS301", "Data Structures", new BigDecimal("4")));
    }

    @Test
    void thePathAloneSaysWhichRecordIsEdited() throws Exception {
        mockMvc.perform(as(manager(), json(put(ROOT + "departments/5"),
                        "{\"id\":9,\"code\":\"HACK\",\"name\":\"Computing\",\"status\":\"INACTIVE\"}")))
                .andExpect(status().isOk());

        verify(departments).update(5L, new DepartmentUpdate("Computing", DepartmentStatus.INACTIVE));
    }

    @Test
    void invalidRequestsAreRefusedBeforeTheService() throws Exception {
        String[][] invalid = {
                {"departments", "{\"code\":\" \",\"name\":\"X\"}"},
                {"departments", "{\"code\":\"" + "C".repeat(21) + "\",\"name\":\"X\"}"},
                {"programs", "{\"code\":\"X\",\"name\":\"X\",\"durationSemesters\":8}"},
                {"programs", "{\"departmentId\":3,\"code\":\"X\",\"name\":\"X\",\"durationSemesters\":21}"},
                {"sessions", "{\"code\":\"2026-27\",\"startDate\":\"01/07/2026\",\"endDate\":\"2027-06-30\"}"},
                {"sessions", "{\"code\":\"2026-27\",\"endDate\":\"2027-06-30\"}"},
                {"semesters", "{\"programId\":4,\"academicSessionId\":2,\"semesterNumber\":0}"},
                {"semesters", "{\"programId\":4,\"semesterNumber\":1}"},
                {"courses", "{\"departmentId\":3,\"code\":\"CS1\",\"name\":\"X\",\"credits\":1.555}"},
                {"courses", "{\"departmentId\":3,\"code\":\"CS1\",\"name\":\"X\",\"credits\":0}"},
                {"courses", "{\"departmentId\":3,\"code\":\"CS1\",\"name\":\"X\",\"credits\":20.5}"}};
        for (String[] request : invalid) {
            mockMvc.perform(as(manager(), json(post(ROOT + request[0]), request[1]))).andExpect(status().isBadRequest());
        }
        mockMvc.perform(as(manager(), json(put(ROOT + "semesters/5"), "{\"status\":\"ARCHIVED\"}"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(manager(), json(put(ROOT + "departments/5"), "{\"name\":\"X\"}"))).andExpect(status().isBadRequest());
        verifyNoServiceCalled();
    }

    @Test
    void refusalsFromTheServicesKeepTheirStatusAndSafeMessage() throws Exception {
        when(courses.create(any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "A course with this code already exists."));
        when(programs.create(any())).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "The selected department does not exist."));
        when(departments.get(eq(99L))).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Department not found."));

        mockMvc.perform(as(manager(), json(post(ROOT + "courses"), COURSE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A course with this code already exists."));
        mockMvc.perform(as(manager(), json(post(ROOT + "programs"), PROGRAM)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The selected department does not exist."));
        mockMvc.perform(as(viewer(), get(ROOT + "departments/99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department not found."));
    }

    @Test
    void thereIsNoDeleteForAnyMaster() throws Exception {
        for (String master : MASTERS) {
            mockMvc.perform(as(manager(), delete(ROOT + master + "/1"))).andExpect(status().isMethodNotAllowed());
        }
        verifyNoServiceCalled();
    }

    @Test
    void everyRouteIsDocumentedWithTheBearerScheme() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        String[] ids = {"departmentId", "programId", "academicSessionId", "semesterId", "courseId"};
        for (int index = 0; index < MASTERS.size(); index++) {
            String collection = "$.paths['" + ROOT + MASTERS.get(index) + "']";
            String item = "$.paths['" + ROOT + MASTERS.get(index) + "/{" + ids[index] + "}']";
            for (String operation : new String[] {collection + ".get", collection + ".post", item + ".get", item + ".put"}) {
                result.andExpect(jsonPath(operation + ".security[0].bearerAuth").exists())
                        .andExpect(jsonPath(operation + ".responses['401']").exists())
                        .andExpect(jsonPath(operation + ".responses['403']").exists());
            }
            result.andExpect(jsonPath(collection + ".post.responses['201']").exists())
                    .andExpect(jsonPath(collection + ".post.responses['400']").exists())
                    .andExpect(jsonPath(collection + ".post.responses['409']").exists())
                    .andExpect(jsonPath(item + ".get.responses['404']").exists())
                    .andExpect(jsonPath(item + ".put.responses['404']").exists())
                    .andExpect(jsonPath(item + ".delete").doesNotExist());
        }
        result.andExpect(jsonPath("$.components.schemas.DepartmentUpdate.properties.code").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.DepartmentUpdate.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SemesterUpdate.properties.semesterNumber").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CourseCreate.properties.semesterId").doesNotExist());
    }
}
