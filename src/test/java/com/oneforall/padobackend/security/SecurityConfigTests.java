package com.oneforall.padobackend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityConfigTests {
    @Test
    void netlifyProductionOriginRemainsAllowedWhenRenderHasAnOlderEnvironmentValue() {
        CorsConfigurationSource source = new SecurityConfig()
                .corsConfigurationSource(
                        "https://pado-shop.onrender.com/",
                        "https://pado-shop.netlify.app/");

        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

        assertEquals("https://pado-shop.netlify.app",
                configuration.checkOrigin("https://pado-shop.netlify.app"));
        assertEquals("https://pado-shop.onrender.com",
                configuration.checkOrigin("https://pado-shop.onrender.com"));
        assertNull(configuration.checkOrigin("https://untrusted.example"));
    }
}
