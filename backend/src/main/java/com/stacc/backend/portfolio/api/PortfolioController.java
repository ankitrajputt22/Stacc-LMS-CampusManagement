package com.stacc.backend.portfolio.api;

import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.portfolio.PortfolioItem;
import com.stacc.backend.portfolio.PortfolioService;
import com.stacc.backend.portfolio.PortfolioService.ItemInput;
import com.stacc.backend.portfolio.PortfolioService.ItemView;
import com.stacc.backend.portfolio.PortfolioService.Portfolio;
import com.stacc.backend.portfolio.PortfolioService.WaitingItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A student's own portfolio, and the verification requests sent to a faculty member. */
@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "Portfolio")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "Not an item of the signed-in account, or not waiting for its verification.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class PortfolioController {

    private static final String USE = "hasRole('STUDENT') and hasAuthority('PORTFOLIO_USE')";
    private static final String VERIFY = "hasRole('FACULTY') and hasAuthority('PORTFOLIO_VERIFY')";

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    public record ItemRequest(@NotNull PortfolioItem.Kind kind, @NotBlank @Size(max = 200) String title,
            @Size(max = PortfolioItem.MAX_DESCRIPTION) String description, LocalDate occurredOn, @Size(max = 500) String link) {

        ItemInput input() {
            return new ItemInput(kind, title, description, occurredOn, link);
        }
    }

    public record VerificationRequest(@NotBlank @Size(max = 100) String verifierLoginId) {
    }

    public record DecisionRequest(@NotNull Boolean verify, @Size(max = 1000) String note) {
    }

    @GetMapping("/me")
    @PreAuthorize(USE)
    @Operation(summary = "My portfolio", description = "Records read live from their sources, and my own items with their verification.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public Portfolio mine(@AuthenticationPrincipal Jwt token) {
        return service.mine(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/items")
    @PreAuthorize(USE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an item", description = "A link must be one https:// address.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ItemView add(@Valid @RequestBody ItemRequest request, @AuthenticationPrincipal Jwt token) {
        return service.add(AuthenticatedAccount.idFrom(token), request.input());
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize(USE)
    @Operation(summary = "Edit an item", description = "Not while verified or waiting for verification.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Verified or waiting for verification.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ItemView edit(@PathVariable Long itemId, @Valid @RequestBody ItemRequest request, @AuthenticationPrincipal Jwt token) {
        return service.edit(AuthenticatedAccount.idFrom(token), itemId, request.input());
    }

    @PostMapping("/items/{itemId}/hide")
    @PreAuthorize(USE)
    @Operation(summary = "Hide an item", description = "It leaves the portfolio and is kept.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ItemView hide(@PathVariable Long itemId, @AuthenticationPrincipal Jwt token) {
        return service.hide(AuthenticatedAccount.idFrom(token), itemId);
    }

    @PostMapping("/items/{itemId}/verification")
    @PreAuthorize(USE)
    @Operation(summary = "Ask a faculty member to verify an item", description = "Once; a declined item is edited before asking again.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Not an active faculty account, or already asked.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ItemView requestVerification(@PathVariable Long itemId, @Valid @RequestBody VerificationRequest request, @AuthenticationPrincipal Jwt token) {
        return service.requestVerification(AuthenticatedAccount.idFrom(token), itemId, request.verifierLoginId());
    }

    @GetMapping("/verifications")
    @PreAuthorize(VERIFY)
    @Operation(summary = "Items waiting for my verification", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<WaitingItem> waiting(@AuthenticationPrincipal Jwt token) {
        return service.waitingFor(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/verifications/{itemId}/decision")
    @PreAuthorize(VERIFY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Verify or decline an item", description = "Only the named faculty member, once.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void decide(@PathVariable Long itemId, @Valid @RequestBody DecisionRequest request, @AuthenticationPrincipal Jwt token) {
        service.decide(AuthenticatedAccount.idFrom(token), itemId, request.verify(), request.note());
    }
}
