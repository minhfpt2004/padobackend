package com.oneforall.padobackend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ServiceInfoController {
    @GetMapping("/")
    Map<String, String> serviceInfo() {
        return Map.of(
                "service", "PADO Backend API",
                "status", "UP",
                "health", "/api/health",
                "documentation", "/swagger-ui/index.html");
    }
}
