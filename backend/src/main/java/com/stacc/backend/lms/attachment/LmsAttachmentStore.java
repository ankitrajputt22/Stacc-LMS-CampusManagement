package com.stacc.backend.lms.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.content.LmsMaterialFiles;
import com.stacc.backend.lms.content.LmsMaterialProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LmsAttachmentStore {

    public static final int MAX_FILES = 5;
    public static final String STORAGE_UNAVAILABLE = "File storage is not available right now. Try again later.";
    private static final Logger LOGGER = LoggerFactory.getLogger(LmsAttachmentStore.class);

    public record StoredFile(String objectKey, String fileName, String contentType, long sizeBytes) {
    }

    public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
    }

    private final ObjectStorageService storage;
    private final long maxFileBytes;

    public LmsAttachmentStore(ObjectStorageService storage, LmsMaterialProperties properties) {
        this.storage = storage;
        this.maxFileBytes = properties.maxFileSize().toBytes();
    }

    public List<StoredFile> storeAll(String keyPrefix, Collection<MultipartFile> files) {
        List<MultipartFile> chosen = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !(file.isEmpty() && (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())))
                .toList();
        if (chosen.size() > MAX_FILES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Attach at most " + MAX_FILES + " files.");
        }
        List<LmsMaterialFiles.CheckedFile> checked = chosen.stream().map(file -> LmsMaterialFiles.check(file, maxFileBytes)).toList();
        List<StoredFile> stored = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            LmsMaterialFiles.CheckedFile file = checked.get(i);
            String key = keyPrefix + "/" + UUID.randomUUID();
            stored.add(new StoredFile(key, file.fileName(), file.type().contentType(), file.sizeBytes()));
            try (InputStream content = chosen.get(i).getInputStream()) {
                storage.store(key, content, file.sizeBytes(), file.type().contentType());
            } catch (IOException failure) {
                removeQuietly(stored);
                throw new ApiException(HttpStatus.BAD_REQUEST, "A file could not be read. Try again.");
            } catch (ObjectStorageException failure) {
                LOGGER.warn("An attachment could not be stored", failure);
                removeQuietly(stored);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE);
            }
        }
        return stored;
    }

    public void removeQuietly(List<StoredFile> files) {
        for (StoredFile file : files) {
            try {
                storage.delete(file.objectKey());
            } catch (RuntimeException failure) {
                LOGGER.warn("A stored attachment could not be removed after its record failed", failure);
            }
        }
    }

    public Download open(String objectKey, String fileName, String contentType, long sizeBytes) {
        try {
            return new Download(fileName, contentType, sizeBytes, storage.open(objectKey));
        } catch (ObjectStorageException failure) {
            LOGGER.warn("An attachment could not be read from storage", failure);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE);
        }
    }
}
