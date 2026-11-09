package com.stacc.backend.lms.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;

import com.stacc.backend.lms.assignment.LmsAssignment;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * A deadline an assignment can be given: between the years 2000 and 9999, so it is a real date the database
 * can store. A missing deadline is left to {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = SupportedDueAt.Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedDueAt {

    String message() default "Choose a due date between the years 2000 and 9999";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SupportedDueAt, Instant> {

        @Override
        public boolean isValid(Instant value, ConstraintValidatorContext context) {
            return value == null || LmsAssignment.isSupportedDueAt(value);
        }
    }
}
