package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.LocationSuggestionResponse;
import com.oneforall.padobackend.service.LocationLookupService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/locations")
public class LocationLookupController {
    private final LocationLookupService locations;

    public LocationLookupController(LocationLookupService locations) {
        this.locations = locations;
    }

    @GetMapping("/reverse")
    LocationSuggestionResponse reverse(@RequestParam double latitude, @RequestParam double longitude) {
        return locations.reverse(latitude, longitude);
    }

    @GetMapping("/search")
    List<LocationSuggestionResponse> search(
            @RequestParam @Size(min = 3, max = 100) String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int limit
    ) {
        return locations.search(query, limit);
    }
}
