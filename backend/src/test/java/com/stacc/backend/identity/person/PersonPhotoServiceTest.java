package com.stacc.backend.identity.person;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.stacc.backend.audit.AuditAction;
import com.stacc.backend.audit.AuditService;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class PersonPhotoServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5};

    private final PersonProfileRepository profiles = mock(PersonProfileRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final AuditService audit = mock(AuditService.class);
    private final PersonPhotoService service = new PersonPhotoService(profiles, storage, audit,
            Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC));

    private PersonProfile recorded() {
        UserAccount account = new UserAccount("25CSE104", "{test}hash");
        ReflectionTestUtils.setField(account, "id", 57L);
        PersonProfile profile = new PersonProfile(account, "Riya Menon", null);
        when(profiles.findByUserAccountId(57L)).thenReturn(Optional.of(profile));
        return profile;
    }

    @Test
    void aPngIsStoredUnderAServerMadeKeyAndRecorded() {
        PersonProfile profile = recorded();

        service.upload(1L, 57L, new MockMultipartFile("file", "../../me.exe", "application/octet-stream", PNG));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).store(key.capture(), any(InputStream.class), eq((long) PNG.length), eq("image/png"));
        assertTrue(key.getValue().matches("people/57/photo/[0-9a-f-]{36}"), key.getValue());
        assertEquals(key.getValue(), profile.getPhotoObjectKey());
        verify(audit).record(1L, AuditAction.USER_PHOTO_CHANGED, AuditService.USER_ACCOUNT, 57L, "Official photo changed.");
    }

    @Test
    void theTypeComesFromTheBytesNotTheName() {
        assertEquals("image/jpeg", PersonPhotoService.check(new MockMultipartFile("file", "photo.png", "image/png", JPEG)));
    }

    @Test
    void anythingButASmallJpegOrPngIsRefusedWithoutStoring() {
        recorded();
        for (MockMultipartFile bad : new MockMultipartFile[] {
                new MockMultipartFile("file", "a.png", "image/png", "<svg/>".getBytes()),
                new MockMultipartFile("file", "a.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "a.png", "image/png", new byte[(int) PersonPhotoService.MAX_BYTES + 1])}) {
            ApiException refused = assertThrows(ApiException.class, () -> service.upload(1L, 57L, bad));
            assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
        }
        verify(storage, never()).store(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void aPhotoNeedsARecordedProfile() {
        ApiException refused = assertThrows(ApiException.class,
                () -> service.upload(1L, 99L, new MockMultipartFile("file", "a.png", "image/png", PNG)));
        assertEquals(HttpStatus.CONFLICT, refused.getStatus());
    }

    @Test
    void anAccountWithoutAPhotoHasNoneToOpen() {
        recorded();
        assertTrue(service.open(57L).isEmpty());
        assertTrue(service.open(99L).isEmpty());
    }
}
