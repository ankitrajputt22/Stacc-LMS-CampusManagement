package com.stacc.backend.identity.person;

import io.swagger.v3.oas.annotations.media.Schema;

public final class PersonProfileResponses {

    private PersonProfileResponses() {
    }

    @Schema(description = "The signed-in person's own profile. Contact details are shown only to the person themselves "
            + "(and later to authorized college staff), never to other students or faculty.")
    public record MyPersonProfile(
            @Schema(example = "2408400100011") String loginId,
            @Schema(description = "The name to show: the preferred name, else the full name, else the login ID while the "
                    + "college has not recorded a profile.", example = "Aarav Sharma") String displayName,
            @Schema(description = "False until the college records the profile; then the fields below can be filled.")
            boolean recorded,
            @Schema(nullable = true, description = "As recorded by the college.") String fullName,
            @Schema(nullable = true, description = "Chosen by the person.") String preferredName,
            @Schema(nullable = true, description = "As recorded by the college.") String email,
            @Schema(nullable = true, description = "Chosen by the person.") String phone,
            @Schema(description = "Whether the college has added an official photo.") boolean hasPhoto) {

        public static MyPersonProfile of(String loginId, PersonProfile profile) {
            return new MyPersonProfile(loginId, profile.displayName(), true, profile.getFullName(),
                    profile.getPreferredName(), profile.getEmail(), profile.getPhone(), profile.hasPhoto());
        }

        public static MyPersonProfile notRecorded(String loginId) {
            return new MyPersonProfile(loginId, loginId, false, null, null, null, null, false);
        }
    }
}
