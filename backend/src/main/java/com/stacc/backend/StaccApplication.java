package com.stacc.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Stacc never uses Spring Boot's generated development user. Accounts come from UserAccount.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class StaccApplication {

    public static void main(String[] args) {
        SpringApplication.run(StaccApplication.class, args);
    }
}
