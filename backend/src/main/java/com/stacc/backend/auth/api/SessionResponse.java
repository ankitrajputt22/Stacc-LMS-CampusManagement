package com.stacc.backend.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Who is signed in, as the page needs it. The token itself is never in a response body: browsers keep it in the
 * HttpOnly session cookie, where page scripts cannot read it.
 */
public record SessionResponse(
        @Schema(example = "12") Long accountId,
        @Schema(example = "2408400100011") String loginId,
        @Schema(description = "The roles the college has assigned to this account.", example = "[\"STUDENT\"]")
        List<String> roles,
        @Schema(description = "When this sign-in ends at the latest. It can end earlier: after the idle timeout, when "
                + "the person signs out, or when the college disables the account.")
        Instant expiresAt) {
}
