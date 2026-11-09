package com.stacc.backend.todo;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.todo.TodoService.TodoItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's own To-Do list, worked out live. */
@RestController
@RequestMapping("/api/v1/me/todo")
@Tag(name = "To-Do")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account cannot use the To-Do list.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class TodoController {

    private static final String ALLOWED = "hasAnyRole('STUDENT', 'FACULTY', 'ADMIN') and hasAuthority('TODO_VIEW')";

    @Schema(description = "The key of the item to hide.")
    public record DismissRequest(@NotBlank @Size(max = 100) String key) {
    }

    private final TodoService todo;

    public TodoController(TodoService todo) {
        this.todo = todo;
    }

    @GetMapping
    @PreAuthorize(ALLOWED)
    @Operation(summary = "Read your To-Do list",
            description = "Unsubmitted assignments, work waiting for grading, open attendance, and pending approval tasks, each "
                    + "only for the roles and permissions the account holds; dismissed items are left out. Soonest due first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The items. The list may be empty.")
    public List<TodoItem> list(@AuthenticationPrincipal Jwt token, Authentication authentication) {
        return todo.list(AuthenticatedAccount.idFrom(token), authentication.getAuthorities());
    }

    @PostMapping("/dismissals")
    @PreAuthorize(ALLOWED)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dismiss a To-Do item", description = "Hides it from your list. Dismissing again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void dismiss(@Valid @RequestBody DismissRequest request, @AuthenticationPrincipal Jwt token) {
        todo.dismiss(AuthenticatedAccount.idFrom(token), request.key());
    }
}
