package com.stacc.backend.auth.api;

import com.stacc.backend.auth.account.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(description = "The login ID issued by the college.", example = "2408400100011")
        @NotBlank(message = "Login ID is required")
        @Size(max = UserAccount.MAX_LOGIN_ID_LENGTH, message = "Login ID is too long")
        String loginId,

        @Schema(description = "The account password.", format = "password")
        @NotBlank(message = "Password is required")
        @Size(max = LoginRequest.MAX_PASSWORD_LENGTH, message = "Password is too long")
        String password) {

    public static final int MAX_PASSWORD_LENGTH = 128;

    // A record's default text form would include the password, so it is left out here.
    @Override
    public String toString() {
        return "LoginRequest[loginId=" + loginId + "]";
    }
}
