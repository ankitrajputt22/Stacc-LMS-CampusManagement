package com.stacc.backend.auth.api;

import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "College login",
            description = "Checks a college-issued login ID and password and returns a short-lived access "
                    + "token. The account's roles come from the college's records and are never chosen by the user.")
    @ApiResponse(responseCode = "200", description = "The login ID and password are correct.")
    @ApiResponse(
            responseCode = "400",
            description = "The request is missing a field or is malformed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "The login ID or password is wrong.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request);
    }
}
