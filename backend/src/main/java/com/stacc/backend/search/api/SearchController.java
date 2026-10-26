package com.stacc.backend.search.api;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.search.GlobalSearchService;
import com.stacc.backend.search.SearchActor;
import com.stacc.backend.search.SearchResponses.GlobalSearchResponse;
import com.stacc.backend.search.SearchResponses.SearchResultPage;
import com.stacc.backend.search.SearchResultType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global search. The caller, its roles, and its permissions come from the access token; no request names an account.
 * GLOBAL_SEARCH_USE only lets an account use search: every kind of result is still decided by its own domain's rules,
 * and a kind the account may not see is simply absent.
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search")
@ApiResponse(
        responseCode = "400",
        description = "The search text is blank, shorter than 2 characters, or longer than 100; or the type, page, or size is not valid.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account may not use search (it needs GLOBAL_SEARCH_USE with STUDENT, FACULTY, or ADMIN).",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class SearchController {

    static final String USE = "hasAnyRole('STUDENT', 'FACULTY', 'ADMIN') and hasAuthority('GLOBAL_SEARCH_USE')";

    private final GlobalSearchService search;

    public SearchController(GlobalSearchService search) {
        this.search = search;
    }

    @GetMapping
    @PreAuthorize(USE)
    @Operation(summary = "Search Stacc",
            description = "Searches what the signed-in account may already see, grouped by kind in a fixed order: up to 5 results of each kind with the kind's total. Kinds without results, and kinds the account may not see, are left out. The text is trimmed, its spaces collapsed, and matched case-insensitively and literally (% and _ are ordinary characters). Nothing is stored.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The grouped results. The list of sections may be empty.")
    public GlobalSearchResponse search(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @Parameter(description = "The search text: 2 to 100 characters.", example = "data")
            @RequestParam(required = false) String q) {
        return search.preview(actor(accessToken, authentication), q);
    }

    @GetMapping("/results")
    @PreAuthorize(USE)
    @Operation(summary = "Search one kind of result",
            description = "One page of one kind of result, under exactly the rules of the grouped search, for View all. A kind the account may not see is an empty page.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of results. The list may be empty.")
    public SearchResultPage results(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @Parameter(description = "The search text: 2 to 100 characters.", example = "data")
            @RequestParam(required = false) String q,
            @Parameter(description = "The kind of result.") @RequestParam(required = false) SearchResultType type,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return search.results(actor(accessToken, authentication), q, type, page, size);
    }

    private static SearchActor actor(Jwt accessToken, Authentication authentication) {
        return SearchActor.of(AuthenticatedAccount.idFrom(accessToken), authentication.getAuthorities());
    }
}
