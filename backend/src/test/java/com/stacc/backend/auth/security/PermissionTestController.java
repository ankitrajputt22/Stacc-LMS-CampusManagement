package com.stacc.backend.auth.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exists only in test code. Each route requires a made-up TEST_ permission, so tests can
 * check permission rules without adding real permissions or demo endpoints to the application.
 */
@RestController
@RequestMapping("/api/test/permissions")
public class PermissionTestController {

    @GetMapping("/read")
    @PreAuthorize("hasAuthority('TEST_READ')")
    public String read() {
        return "read";
    }

    @GetMapping("/write")
    @PreAuthorize("hasAuthority('TEST_WRITE')")
    public String write() {
        return "write";
    }

    @GetMapping("/read-or-shared")
    @PreAuthorize("hasAnyAuthority('TEST_READ', 'TEST_SHARED')")
    public String readOrShared() {
        return "read-or-shared";
    }

    @GetMapping("/faculty-write")
    @PreAuthorize("hasRole('FACULTY') and hasAuthority('TEST_WRITE')")
    public String facultyWrite() {
        return "faculty-write";
    }
}
