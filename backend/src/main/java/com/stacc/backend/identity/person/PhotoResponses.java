package com.stacc.backend.identity.person;

import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Sends an official photo as an image, never cached by shared caches and never sniffed as anything else. */
public final class PhotoResponses {

    private PhotoResponses() {
    }

    public static ResponseEntity<InputStreamResource> of(Optional<PersonPhotoService.Photo> photo) {
        PersonPhotoService.Photo found = photo
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PersonPhotoService.NO_PHOTO));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(found.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(found.content()));
    }
}
