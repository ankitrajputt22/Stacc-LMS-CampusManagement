package com.stacc.backend.support.helpdesk.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.support.helpdesk.HelpdeskArticle;
import com.stacc.backend.support.helpdesk.HelpdeskArticleService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-help articles of the helpdesk: suggestions for someone writing a ticket, and the admins' article list with add,
 * edit, publish, and archive. Nothing deletes an article.
 */
@RestController
@Validated
@Tag(name = "Helpdesk articles")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HelpdeskArticleController {

    private final HelpdeskArticleService service;

    public HelpdeskArticleController(HelpdeskArticleService service) {
        this.service = service;
    }

    @Schema(description = "A published article, as requesters read it. Plain text.")
    public record ArticleResponse(Long articleId, Long categoryId, String categoryName, String title, String body,
            Instant publishedAt) {

        static ArticleResponse of(HelpdeskArticle article) {
            return new ArticleResponse(article.getId(), article.getCategory().getId(), article.getCategory().getName(),
                    article.getTitle(), article.getBody(), article.getPublishedAt());
        }
    }

    @Schema(description = "An article as admins manage it.")
    public record AdminArticleResponse(Long articleId, Long categoryId, String categoryName, boolean categoryActive,
            String title, String body, HelpdeskArticle.Status status, Instant publishedAt, Instant updatedAt) {

        static AdminArticleResponse of(HelpdeskArticle article) {
            return new AdminArticleResponse(article.getId(), article.getCategory().getId(), article.getCategory().getName(),
                    article.getCategory().isActive(), article.getTitle(), article.getBody(), article.getStatus(),
                    article.getPublishedAt(), article.getUpdatedAt());
        }
    }

    public record ArticleRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = HelpdeskArticle.MAX_TITLE_LENGTH) String title,
            @NotBlank @Size(max = HelpdeskArticle.MAX_BODY_LENGTH) String body) {
    }

    @GetMapping("/api/v1/helpdesk/articles")
    @PreAuthorize(HelpdeskAccess.CREATE)
    @Operation(summary = "Help articles for a ticket being written",
            description = "Published articles of the active category; with q (the subject), those sharing the most words "
                    + "first and none that share none. At most 5. Only reads.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The articles. The list may be empty.")
    public List<ArticleResponse> suggestions(@RequestParam Long categoryId,
            @RequestParam(required = false) @Size(max = 200) String q) {
        return service.suggestions(categoryId, q).stream().map(ArticleResponse::of).toList();
    }

    @GetMapping("/api/v1/helpdesk/articles/{articleId}")
    @PreAuthorize(HelpdeskAccess.CREATE)
    @Operation(summary = "Read a help article", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The article.")
    @ApiResponse(responseCode = "404", description = "No such published article in an active category.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ArticleResponse article(@PathVariable Long articleId) {
        return ArticleResponse.of(service.published(articleId));
    }

    @GetMapping("/api/v1/helpdesk/admin/articles")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "Every help article", description = "Drafts, published, and archived, by category.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<AdminArticleResponse> all() {
        return service.all().stream().map(AdminArticleResponse::of).toList();
    }

    @PostMapping("/api/v1/helpdesk/admin/articles")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a help article", description = "It starts as a draft.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new article.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long, or the category does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminArticleResponse add(@Valid @RequestBody ArticleRequest request, @AuthenticationPrincipal Jwt token) {
        return AdminArticleResponse.of(service.add(AuthenticatedAccount.idFrom(token), request.categoryId(), request.title(), request.body()));
    }

    @PutMapping("/api/v1/helpdesk/admin/articles/{articleId}")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "Change a help article", description = "A published article stays published.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The article.")
    @ApiResponse(responseCode = "409", description = "The article is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminArticleResponse edit(@PathVariable Long articleId, @Valid @RequestBody ArticleRequest request,
            @AuthenticationPrincipal Jwt token) {
        return AdminArticleResponse.of(service.edit(AuthenticatedAccount.idFrom(token), articleId, request.categoryId(),
                request.title(), request.body()));
    }

    @PostMapping("/api/v1/helpdesk/admin/articles/{articleId}/publish")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "Publish a help article", description = "Requesters see it while its category is active.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The article.")
    @ApiResponse(responseCode = "409", description = "The article is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminArticleResponse publish(@PathVariable Long articleId, @AuthenticationPrincipal Jwt token) {
        return AdminArticleResponse.of(service.publish(AuthenticatedAccount.idFrom(token), articleId));
    }

    @PostMapping("/api/v1/helpdesk/admin/articles/{articleId}/archive")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "Archive a help article", description = "It is kept but no longer shown or changed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The article.")
    public AdminArticleResponse archive(@PathVariable Long articleId, @AuthenticationPrincipal Jwt token) {
        return AdminArticleResponse.of(service.archive(AuthenticatedAccount.idFrom(token), articleId));
    }
}
