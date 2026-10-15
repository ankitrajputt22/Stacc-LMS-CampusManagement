package com.stacc.backend.lms.api;

import static com.stacc.backend.lms.content.ContentFixtures.material;
import static com.stacc.backend.lms.content.ContentFixtures.module;
import static com.stacc.backend.lms.content.ContentFixtures.topic;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;

/**
 * Calls the material routes through Spring Security. The service is a stand-in, so these tests are about
 * who gets in, what is checked at the door, and what goes back, including the download headers.
 */
class LmsMaterialControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 21;
    private static final byte[] PDF = "%PDF-1.7\nnotes".getBytes(StandardCharsets.US_ASCII);

    @MockitoBean
    private LmsStudyMaterialService materialService;

    private final LmsStudyMaterial material = material(
            topic(module(ContentFixtures.lmsCourse(7), 11, 1, LmsContentStatus.DRAFT), 31, 1, LmsContentStatus.DRAFT),
            7, 1, LmsContentStatus.DRAFT);

    private static String tokenWith(String... authorities) {
        return TestTokens.valid("TEST-USER-1", ACCOUNT_ID, authorities);
    }

    private static String managerToken() {
        return tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE");
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<?>> T as(String token, T request) {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private static MockMultipartHttpServletRequestBuilder upload(String title, MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/lms/faculty/topics/31/materials");
        if (file != null) {
            request.file(file);
        }
        if (title != null) {
            request.param("title", title);
        }
        return request;
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "Notes.pdf", "application/pdf", PDF);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<AbstractMockHttpServletRequestBuilder<?>> changeRoutes() {
        return List.<AbstractMockHttpServletRequestBuilder<?>>of(
                upload("Notes", pdf()),
                json(put("/api/lms/faculty/topics/31/materials/reorder"), "{\"materialIds\":[7]}"),
                json(put("/api/lms/faculty/materials/7"), "{\"title\":\"Notes\"}"),
                post("/api/lms/faculty/materials/7/publish"),
                post("/api/lms/faculty/materials/7/archive"));
    }

    // Upload and changes

    @Test
    void uploadAnswersCreatedWithTheMaterialButNeverItsStorageKey() throws Exception {
        when(materialService.upload(eq(ACCOUNT_ID), eq(31L), eq("Lecture notes"), eq("Read first."), any())).thenReturn(material);

        mockMvc.perform(as(managerToken(), upload("Lecture notes", pdf())
                        .param("description", "Read first.")
                        .param("objectKey", "evil/key")
                        .param("status", "PUBLISHED")
                        .param("lmsCourseId", "99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.originalFileName").value("material-7.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(1007))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.*", hasSize(8)))
                .andExpect(content().string(not(containsString("key"))))
                .andExpect(content().string(not(containsString("bucket"))));

        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        verify(materialService).upload(eq(ACCOUNT_ID), eq(31L), eq("Lecture notes"), eq("Read first."), file.capture());
        assertEquals("Notes.pdf", file.getValue().getOriginalFilename());
    }

    @Test
    void uploadNeedsATitleAndAFileBeforeAnythingHappens() throws Exception {
        for (AbstractMockHttpServletRequestBuilder<?> request : List.<AbstractMockHttpServletRequestBuilder<?>>of(
                upload(null, pdf()),
                upload("  ", pdf()),
                upload("Notes", null),
                upload("x".repeat(201), pdf()),
                upload("Notes", pdf()).param("description", "x".repeat(1001)))) {
            mockMvc.perform(as(managerToken(), request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        }
        mockMvc.perform(as(managerToken(), json(post("/api/lms/faculty/topics/31/materials"), "{\"title\":\"Notes\"}")))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(materialService);
    }

    @Test
    void refusalsFromTheServiceKeepTheirStatusAndMessage() throws Exception {
        when(materialService.upload(anyLong(), eq(31L), any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "The file's contents do not match its type."));
        when(materialService.publishMaterial(anyLong(), eq(7L)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Study material not found or unavailable."));

        mockMvc.perform(as(managerToken(), upload("Notes", pdf())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The file's contents do not match its type."));
        mockMvc.perform(as(managerToken(), post("/api/lms/faculty/materials/7/publish")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Study material not found or unavailable."));
    }

    @Test
    void eachChangeRouteCallsItsActionForTheAccountInTheToken() throws Exception {
        when(materialService.reorderMaterials(ACCOUNT_ID, 31L, List.of(7L))).thenReturn(List.of(material));
        when(materialService.updateMaterial(ACCOUNT_ID, 7L, "Notes", null)).thenReturn(material);
        when(materialService.publishMaterial(ACCOUNT_ID, 7L)).thenReturn(material);
        when(materialService.archiveMaterial(ACCOUNT_ID, 7L)).thenReturn(material);

        for (AbstractMockHttpServletRequestBuilder<?> route : changeRoutes().subList(1, 5)) {
            mockMvc.perform(as(managerToken(), route)).andExpect(status().isOk());
        }

        verify(materialService).reorderMaterials(ACCOUNT_ID, 31L, List.of(7L));
        verify(materialService).updateMaterial(eq(ACCOUNT_ID), eq(7L), eq("Notes"), isNull());
        verify(materialService).publishMaterial(ACCOUNT_ID, 7L);
        verify(materialService).archiveMaterial(ACCOUNT_ID, 7L);
    }

    @Test
    void changesNeedTheFacultyRoleAndTheExistingContentPermission() throws Exception {
        for (AbstractMockHttpServletRequestBuilder<?> route : changeRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (String token : List.of(
                tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW"),
                tokenWith("LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_ADMIN", "LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_FACULTY", "LMS_MATERIAL_MANAGE"))) {
            for (AbstractMockHttpServletRequestBuilder<?> route : changeRoutes()) {
                mockMvc.perform(as(token, route)).andExpect(status().isForbidden());
            }
        }

        verifyNoInteractions(materialService);
    }

    @Test
    void materialsCannotBeDeletedOrHaveTheirFileReplaced() throws Exception {
        mockMvc.perform(as(managerToken(), delete("/api/lms/faculty/materials/7"))).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(as(managerToken(), multipart("/api/lms/faculty/materials/7").file(pdf())))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(materialService);
    }

    // Download

    @Test
    void downloadIsSentAsAnAttachmentWithItsStoredDetails() throws Exception {
        when(materialService.openForDownload(eq(ACCOUNT_ID), any(), eq(7L))).thenReturn(new LmsStudyMaterialService.Download(
                "Week 1 – Notes.pdf", "application/pdf", PDF.length, new ByteArrayInputStream(PDF)));

        mockMvc.perform(as(tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW"), get("/api/lms/materials/7/download")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PDF.length))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''Week%201%20%E2%80%93%20Notes.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void downloadUsesTheRulesTheTokenAllows() throws Exception {
        when(materialService.openForDownload(anyLong(), any(), anyLong())).thenAnswer(call -> new LmsStudyMaterialService.Download(
                "Notes.pdf", "application/pdf", PDF.length, new ByteArrayInputStream(PDF)));

        mockMvc.perform(as(tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW"), get("/api/lms/materials/7/download"))).andExpect(status().isOk());
        mockMvc.perform(as(tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW"), get("/api/lms/materials/7/download"))).andExpect(status().isOk());
        mockMvc.perform(as(tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW", "ROLE_FACULTY", "FACULTY_COURSE_VIEW"),
                get("/api/lms/materials/7/download"))).andExpect(status().isOk());
        mockMvc.perform(as(tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW", "ROLE_FACULTY"),
                get("/api/lms/materials/7/download"))).andExpect(status().isOk());

        // The last token holds FACULTY without its view permission, so it opens the student rule only.
        verify(materialService, times(2)).openForDownload(ACCOUNT_ID, new LmsStudyMaterialService.Reader(true, false), 7L);
        verify(materialService).openForDownload(ACCOUNT_ID, new LmsStudyMaterialService.Reader(false, true), 7L);
        verify(materialService).openForDownload(ACCOUNT_ID, new LmsStudyMaterialService.Reader(true, true), 7L);
    }

    @Test
    void downloadNeedsAStudentOrFacultyCourseViewer() throws Exception {
        mockMvc.perform(get("/api/lms/materials/7/download")).andExpect(status().isUnauthorized());
        for (String token : List.of(
                tokenWith("ROLE_STUDENT"),
                tokenWith("ROLE_FACULTY"),
                tokenWith("LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW"),
                tokenWith("ROLE_ADMIN", "LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_FACULTY", "LMS_COURSE_VIEW"))) {
            mockMvc.perform(as(token, get("/api/lms/materials/7/download"))).andExpect(status().isForbidden());
        }

        verifyNoInteractions(materialService);
    }

    @Test
    void unavailableMaterialKeepsTheSafeMessage() throws Exception {
        when(materialService.openForDownload(anyLong(), any(), eq(7L)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Study material not found or unavailable."));

        mockMvc.perform(as(tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW"), get("/api/lms/materials/7/download")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Study material not found or unavailable."));
    }

    @Test
    void materialRoutesAppearInTheApiDocumentation() throws Exception {
        String upload = "$.paths['/api/lms/faculty/topics/{topicId}/materials'].post";
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath(upload + ".requestBody.content['multipart/form-data'].schema").exists())
                .andExpect(jsonPath(upload + ".responses['201']").exists())
                .andExpect(jsonPath(upload + ".responses['400']").exists())
                .andExpect(jsonPath(upload + ".responses['413']").exists())
                .andExpect(jsonPath("$.paths['/api/lms/materials/{materialId}/download'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/lms/materials/{materialId}/download'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/lms/faculty/materials/{materialId}'].delete").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LmsFacultyMaterialResponse.properties.objectKey").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LmsCourseMaterialResponse.properties.objectKey").doesNotExist());
        for (String[] operation : new String[][] {
                {"/api/lms/faculty/topics/{topicId}/materials", "post"},
                {"/api/lms/faculty/topics/{topicId}/materials/reorder", "put"},
                {"/api/lms/faculty/materials/{materialId}", "put"},
                {"/api/lms/faculty/materials/{materialId}/publish", "post"},
                {"/api/lms/faculty/materials/{materialId}/archive", "post"},
                {"/api/lms/materials/{materialId}/download", "get"}}) {
            String path = "$.paths['" + operation[0] + "']." + operation[1];
            result.andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
                    .andExpect(jsonPath(path + ".responses['401']").exists())
                    .andExpect(jsonPath(path + ".responses['403']").exists())
                    .andExpect(jsonPath(path + ".responses['404']").exists());
        }
    }
}
