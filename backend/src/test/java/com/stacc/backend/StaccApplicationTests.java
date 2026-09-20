package com.stacc.backend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class StaccApplicationTests {

    @Test
    void applicationHasSpringBootConfiguration() {
        assertTrue(StaccApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }
}
