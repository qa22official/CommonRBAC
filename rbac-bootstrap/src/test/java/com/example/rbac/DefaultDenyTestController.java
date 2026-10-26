package com.example.rbac;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only endpoint deliberately missing an authorization annotation. The
 * interceptor must deny it with 40300 (default deny).
 */
@RestController
@RequestMapping("/api/test")
public class DefaultDenyTestController {

    @GetMapping("/unprotected")
    public Map<String, String> unprotected() {
        return Map.of("ok", "true");
    }
}
