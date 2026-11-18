package com.stacc.backend.auth.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test/roles")
public class RoleTestController {

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public String studentOnly() {
        return "student";
    }

    @GetMapping("/faculty")
    @PreAuthorize("hasRole('FACULTY')")
    public String facultyOnly() {
        return "faculty";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "admin";
    }

    @GetMapping("/student-or-faculty")
    @PreAuthorize("hasAnyRole('STUDENT', 'FACULTY')")
    public String studentOrFaculty() {
        return "student-or-faculty";
    }
}
