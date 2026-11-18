package com.stacc.backend.academic.admin;

import java.util.function.Supplier;

import com.stacc.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

final class AcademicMasterErrors {

    private AcademicMasterErrors() {
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    static <T> T savedOrConflict(Supplier<T> save, String conflictMessage) {
        try {
            return save.get();
        } catch (DataIntegrityViolationException clash) {
            throw conflict(conflictMessage);
        }
    }
}
