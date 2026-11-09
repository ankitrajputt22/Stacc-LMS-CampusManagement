package com.stacc.backend.common.error;

public record FieldValidationError(String field, String message) {
}
