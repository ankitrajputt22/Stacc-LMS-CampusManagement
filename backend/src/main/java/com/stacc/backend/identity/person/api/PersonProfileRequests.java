package com.stacc.backend.identity.person.api;

import com.stacc.backend.identity.person.PersonProfileValues;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public final class PersonProfileRequests {

    private PersonProfileRequests() {
    }

    @Schema(description = "Everything a person may change about themselves. Both are replaced; blank or missing clears.")
    public record ChangeMyPersonalDetailsRequest(
            @Schema(nullable = true, example = "Aarav")
            @Size(max = PersonProfileValues.MAX_PREFERRED_NAME, message = "Preferred name must be at most 80 characters")
            String preferredName,

            @Schema(nullable = true, example = "+91 98765 43210")
            @Size(max = PersonProfileValues.MAX_PHONE, message = "Phone number must be at most 20 characters")
            String phone) {
    }
}
