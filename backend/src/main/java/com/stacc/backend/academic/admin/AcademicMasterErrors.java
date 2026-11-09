package com.stacc.backend.academic.admin;

import java.util.function.Supplier;

import com.stacc.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/** The refusals the academic-master services answer with. Every message is safe to show. */
final class AcademicMasterErrors {

    private AcademicMasterErrors() {
    }

    /** The record named in the route does not exist. */
    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    /** The request is not valid, such as a parent that does not exist. Nothing is saved. */
    static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /** The request clashes with what is stored, such as a code that is already used. Nothing is saved. */
    static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    /**
     * Saves through the database's own unique constraints. The friendly checks before this catch the usual case;
     * when two requests race, the constraint refuses the second, and it gets the same safe 409 instead of a raw error.
     */
    static <T> T savedOrConflict(Supplier<T> save, String conflictMessage) {
        try {
            return save.get();
        } catch (DataIntegrityViolationException clash) {
            throw conflict(conflictMessage);
        }
    }
}
