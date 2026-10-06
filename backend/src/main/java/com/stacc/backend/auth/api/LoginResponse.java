package com.stacc.backend.auth.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(example = "12")
        Long accountId,

        @Schema(example = "2408400100011")
        String loginId,

        @Schema(description = "The roles the college has assigned to this account.", example = "[\"STUDENT\"]")
        List<String> roles) {
}
