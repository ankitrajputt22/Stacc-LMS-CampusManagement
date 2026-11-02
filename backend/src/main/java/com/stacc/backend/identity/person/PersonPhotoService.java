package com.stacc.backend.identity.person;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import com.stacc.backend.audit.AuditAction;
import com.stacc.backend.audit.AuditService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The official photo on a person profile. The college uploads it; the person and admins with {@code USER_VIEW} see it.
 * Only JPEG and PNG images up to 2 MB are taken, checked by their first bytes, and stored in the private bucket under a
 * server-made key that never leaves the backend. A replaced photo's file is kept.
 */
@Service
public class PersonPhotoService {

    public static final long MAX_BYTES = 2L * 1024 * 1024;
    static final String NO_PROFILE = "Record the person's official details before adding a photo.";
    static final String NO_FILE = "Choose a photo to upload.";
    static final String TOO_LARGE = "The photo must be at most 2 MB.";
    static final String NOT_AN_IMAGE = "The photo must be a JPEG or PNG image.";
    static final String NO_PHOTO = "No photo has been added.";
    static final String STORAGE_UNAVAILABLE = "Photo storage is not available right now. Try again later.";

    private static final Logger LOGGER = LoggerFactory.getLogger(PersonPhotoService.class);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final PersonProfileRepository profiles;
    private final ObjectStorageService storage;
    private final AuditService audit;
    private final Clock clock;

    public PersonPhotoService(PersonProfileRepository profiles, ObjectStorageService storage, AuditService audit,
            Clock clock) {
        this.profiles = profiles;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    /** An opened photo. The caller closes the stream. */
    public record Photo(String contentType, InputStream content) {
    }

    /** Stores a new official photo for the account, by an admin from the token, and records it in the audit log. */
    @Transactional
    public void upload(Long actorAccountId, Long accountId, MultipartFile file) {
        PersonProfile profile = profiles.findByUserAccountId(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NO_PROFILE));
        String contentType = check(file);
        String key = "people/" + accountId + "/photo/" + UUID.randomUUID();
        try (InputStream content = file.getInputStream()) {
            storage.store(key, content, file.getSize(), contentType);
        } catch (IOException unreadable) {
            throw new ApiException(HttpStatus.BAD_REQUEST, NOT_AN_IMAGE);
        } catch (ObjectStorageException failure) {
            LOGGER.warn("An official photo could not be stored", failure);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE);
        }
        try {
            profile.changePhoto(key, contentType, clock.instant());
            profiles.saveAndFlush(profile);
            audit.record(actorAccountId, AuditAction.USER_PHOTO_CHANGED, AuditService.USER_ACCOUNT, accountId,
                    "Official photo changed.");
        } catch (RuntimeException failure) {
            removeQuietly(key);
            throw failure;
        }
    }

    /** The account's official photo, or empty when there is none. */
    @Transactional(readOnly = true)
    public Optional<Photo> open(Long accountId) {
        Optional<PersonProfile> profile = profiles.findByUserAccountId(accountId).filter(PersonProfile::hasPhoto);
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Photo(profile.get().getPhotoContentType(), storage.open(profile.get().getPhotoObjectKey())));
        } catch (ObjectStorageException failure) {
            LOGGER.warn("An official photo could not be read from storage", failure);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE);
        }
    }

    // JPEG or PNG by the first bytes, whatever the browser declared; the stored type is the one the bytes show.
    static String check(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, NO_FILE);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, TOO_LARGE);
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(PNG.length);
        } catch (IOException unreadable) {
            throw new ApiException(HttpStatus.BAD_REQUEST, NOT_AN_IMAGE);
        }
        if (startsWith(head, PNG)) {
            return "image/png";
        }
        if (startsWith(head, JPEG)) {
            return "image/jpeg";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, NOT_AN_IMAGE);
    }

    private static boolean startsWith(byte[] head, byte[] magic) {
        if (head.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private void removeQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException failure) {
            LOGGER.warn("A stored photo could not be removed after a failed save", failure);
        }
    }
}
