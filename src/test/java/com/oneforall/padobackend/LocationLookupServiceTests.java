package com.oneforall.padobackend;

import com.oneforall.padobackend.dto.LocationSuggestionResponse;
import com.oneforall.padobackend.service.LocationLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LocationLookupServiceTests {
    @Test
    void reverseLookupReturnsOnlyACompactAdministrativeLabel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocationLookupService service = new LocationLookupService(builder, "https://geocoder.test", "PADO-Test/1.0", 0);

        server.expect(requestTo(containsString("https://geocoder.test/reverse?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, "PADO-Test/1.0"))
                .andRespond(withSuccess("""
                        {"place_id":123,"lat":"10.776","lon":"106.701",
                         "display_name":"42 Secret Road, Bến Nghé, Quận 1",
                         "address":{"house_number":"42","road":"Secret Road","suburb":"Bến Nghé",
                                    "city_district":"Quận 1","city":"Thành phố Hồ Chí Minh","country":"Việt Nam"}}
                        """, MediaType.APPLICATION_JSON));

        LocationSuggestionResponse result = service.reverse(10.776, 106.701);

        assertEquals("Bến Nghé, Quận 1, Thành phố Hồ Chí Minh, Việt Nam", result.label());
        assertEquals(10.776, result.latitude());
        server.verify();
    }

    @Test
    void explicitAddressSearchReturnsCoordinatesAndCachesEqualQueries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocationLookupService service = new LocationLookupService(builder, "https://geocoder.test", "PADO-Test/1.0", 0);

        server.expect(requestTo(containsString("https://geocoder.test/search?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"place_id":456,"lat":"10.735","lon":"106.721",
                          "display_name":"Quận 7, Thành phố Hồ Chí Minh, Việt Nam",
                          "address":{"city_district":"Quận 7","city":"Thành phố Hồ Chí Minh","country":"Việt Nam"}}]
                        """, MediaType.APPLICATION_JSON));

        List<LocationSuggestionResponse> first = service.search("Quận 7", 5);
        List<LocationSuggestionResponse> cached = service.search("  quận 7  ", 5);

        assertEquals(first, cached);
        assertEquals(1, first.size());
        assertEquals("Quận 7, Thành phố Hồ Chí Minh, Việt Nam", first.getFirst().label());
        assertEquals(106.721, first.getFirst().longitude());
        server.verify();
    }
}
