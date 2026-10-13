package com.oneforall.padobackend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oneforall.padobackend.dto.LocationSuggestionResponse;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.LocationLookupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class LocationLookupService {
    private static final int CACHE_SIZE = 250;
    private static final String FALLBACK_LABEL = "Vị trí đã chọn";

    private final RestClient geocoder;
    private final long minimumIntervalMillis;
    private final Object providerLock = new Object();
    private final Map<String, LocationSuggestionResponse> reverseCache = lruCache();
    private final Map<String, List<LocationSuggestionResponse>> searchCache = lruCache();
    private long nextProviderRequestAt;

    public LocationLookupService(
            RestClient.Builder restClientBuilder,
            @Value("${app.geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${app.geocoding.user-agent:PADO-MVP/1.0}") String userAgent,
            @Value("${app.geocoding.minimum-interval-ms:1100}") long minimumIntervalMillis
    ) {
        this.geocoder = restClientBuilder
                .baseUrl(baseUrl.trim())
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent.trim())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "vi")
                .build();
        this.minimumIntervalMillis = Math.max(0, minimumIntervalMillis);
    }

    public LocationSuggestionResponse reverse(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);
        String key = String.format(Locale.ROOT, "%.4f,%.4f", latitude, longitude);
        LocationSuggestionResponse cached = reverseCache.get(key);
        if (cached != null) return cached;

        NominatimPlace place = request(() -> geocoder.get()
                .uri(builder -> builder.path("/reverse")
                        .queryParam("lat", latitude)
                        .queryParam("lon", longitude)
                        .queryParam("format", "jsonv2")
                        .queryParam("zoom", 13)
                        .queryParam("addressdetails", 1)
                        .queryParam("accept-language", "vi")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(NominatimPlace.class));
        if (place == null) throw new LocationLookupException("Không tìm thấy khu vực cho vị trí hiện tại");
        LocationSuggestionResponse result = map(place, latitude, longitude);
        reverseCache.put(key, result);
        return result;
    }

    public List<LocationSuggestionResponse> search(String query, int limit) {
        String cleaned = query == null ? "" : query.trim();
        if (cleaned.length() < 3 || cleaned.length() > 100) {
            throw new BadRequestException("Location query must contain between 3 and 100 characters");
        }
        if (limit < 1 || limit > 10) throw new BadRequestException("Location result limit must be between 1 and 10");
        String key = cleaned.toLowerCase(Locale.ROOT) + "|" + limit;
        List<LocationSuggestionResponse> cached = searchCache.get(key);
        if (cached != null) return cached;

        NominatimPlace[] places = request(() -> geocoder.get()
                .uri(builder -> builder.path("/search")
                        .queryParam("q", cleaned)
                        .queryParam("format", "jsonv2")
                        .queryParam("addressdetails", 1)
                        .queryParam("accept-language", "vi")
                        .queryParam("countrycodes", "vn")
                        .queryParam("limit", limit)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(NominatimPlace[].class));

        Map<String, LocationSuggestionResponse> unique = new LinkedHashMap<>();
        if (places != null) {
            for (NominatimPlace place : places) {
                LocationSuggestionResponse result = map(place, null, null);
                unique.putIfAbsent(result.label().toLowerCase(Locale.ROOT), result);
            }
        }
        List<LocationSuggestionResponse> result = List.copyOf(unique.values());
        searchCache.put(key, result);
        return result;
    }

    private <T> T request(Supplier<T> operation) {
        synchronized (providerLock) {
            long waitMillis = nextProviderRequestAt - System.currentTimeMillis();
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new LocationLookupException("Location lookup was interrupted", exception);
                }
            }
            try {
                return operation.get();
            } catch (RestClientException exception) {
                throw new LocationLookupException("Không thể tra cứu vị trí lúc này. Vui lòng thử lại", exception);
            } finally {
                nextProviderRequestAt = System.currentTimeMillis() + minimumIntervalMillis;
            }
        }
    }

    private LocationSuggestionResponse map(NominatimPlace place, Double fallbackLatitude, Double fallbackLongitude) {
        double latitude = parseCoordinate(place.latitude(), fallbackLatitude, "latitude");
        double longitude = parseCoordinate(place.longitude(), fallbackLongitude, "longitude");
        validateCoordinates(latitude, longitude);
        String label = administrativeLabel(place.address());
        if (label.isBlank()) label = cleanLabel(place.displayName());
        if (label.isBlank()) label = FALLBACK_LABEL;
        String placeId = place.placeId() == null
                ? String.format(Locale.ROOT, "%.5f,%.5f", latitude, longitude)
                : String.valueOf(place.placeId());
        return new LocationSuggestionResponse(placeId, label, latitude, longitude);
    }

    private double parseCoordinate(String value, Double fallback, String name) {
        if (value == null || value.isBlank()) {
            if (fallback != null) return fallback;
            throw new LocationLookupException("Location provider returned an invalid " + name);
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new LocationLookupException("Location provider returned an invalid " + name, exception);
        }
    }

    private String administrativeLabel(Map<String, String> address) {
        if (address == null || address.isEmpty()) return "";
        List<List<String>> groups = List.of(
                List.of("neighbourhood", "quarter", "suburb", "village", "municipality"),
                List.of("city_district", "district", "county"),
                List.of("city", "town", "state", "province"),
                List.of("country")
        );
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (List<String> group : groups) {
            for (String key : group) {
                String value = cleanLabel(address.get(key));
                if (!value.isBlank()) {
                    parts.add(value);
                    break;
                }
            }
        }
        return truncate(String.join(", ", parts));
    }

    private String cleanLabel(String value) {
        return value == null ? "" : truncate(value.trim().replaceAll("\\s+", " "));
    }

    private String truncate(String value) {
        return value.length() <= 100 ? value : value.substring(0, 100).trim();
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BadRequestException("Invalid location coordinates");
        }
    }

    private static <K, V> Map<K, V> lruCache() {
        return Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CACHE_SIZE;
            }
        });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimPlace(
            @JsonProperty("place_id") Long placeId,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("lat") String latitude,
            @JsonProperty("lon") String longitude,
            Map<String, String> address
    ) {
    }
}
