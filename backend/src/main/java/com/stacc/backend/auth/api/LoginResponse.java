package com.stacc.backend.auth.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(example = "12")
        Long accountId,

        @Schema(example = "2408400100011")
        String loginId,

        @Schema(description = "The roles the college has assigned to this account.", example = "[\"STUDENT\"]")
        List<String> roles,

        @Schema(description = "A signed access token for this account.")
        String accessToken,

        @Schema(description = "How the access token is sent on later requests.", example = "Bearer")
        String tokenType,

        @Schema(description = "How long the access token stays valid, in seconds.", example = "900")
        long expiresIn) {

    public static final String BEARER = "Bearer";

    // A record's default text form would include the token, so it is left out here.
    @Override
    public String toString() {
        return "LoginResponse[accountId=" + accountId + ", loginId=" + loginId + ", roles=" + roles + "]";
    }
}
