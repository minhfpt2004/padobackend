package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.UpdateUserLocationRequest;
import com.oneforall.padobackend.dto.UserLocationResponse;
import com.oneforall.padobackend.service.UserLocationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations/me")
public class UserLocationController {
    private final UserLocationService locations;

    public UserLocationController(UserLocationService locations) {
        this.locations = locations;
    }

    @GetMapping
    UserLocationResponse findCurrent(Authentication authentication) {
        return locations.findCurrent(authentication.getName());
    }

    @PutMapping
    UserLocationResponse update(@Valid @RequestBody UpdateUserLocationRequest request,
                                Authentication authentication) {
        return locations.update(authentication.getName(), request);
    }

    @DeleteMapping
    UserLocationResponse clear(Authentication authentication) {
        return locations.clear(authentication.getName());
    }
}
