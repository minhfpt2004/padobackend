package com.oneforall.padobackend.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    Map<String, String> health() {
        Integer databaseResult = jdbcTemplate.queryForObject("select 1", Integer.class);
        if (databaseResult == null || databaseResult != 1) {
            throw new IllegalStateException("Database health check failed");
        }
        return Map.of("status", "UP", "database", "UP");
    }
}
