package com.oneforall.padobackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LocationFlowIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    MockMvc mvc;

    @Test
    void currentLocationCanBeStoredRestoredAndCleared() throws Exception {
        String email = "location-" + UUID.randomUUID().toString().substring(0, 8) + "@pado.test";
        String password = "StrongPassword123!";
        AuthTestHelper.registerAndVerify(mvc, email, password);
        String token = login(email, password);

        mvc.perform(get("/api/locations/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/locations/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.label").doesNotExist());

        mvc.perform(put("/api/locations/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"  Quận 7, TP.HCM  ","source":"MANUAL",
                                 "latitude":10.73,"longitude":106.72,"accuracy":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.label").value("Quận 7, TP.HCM"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.latitude").value(10.73))
                .andExpect(jsonPath("$.longitude").value(106.72))
                .andExpect(jsonPath("$.accuracy").doesNotExist());

        mvc.perform(put("/api/locations/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Quận 3\",\"source\":\"MANUAL\",\"latitude\":10.78}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationLabel").value("Quận 7, TP.HCM"))
                .andExpect(jsonPath("$.latitude").doesNotExist());

        mvc.perform(put("/api/locations/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"District 1\",\"source\":\"GPS\",\"latitude\":10.77,\"longitude\":106.70}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/locations/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"District 1\",\"source\":\"GPS\",\"latitude\":190,\"longitude\":106.70,\"accuracy\":25}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/locations/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Bến Nghé, Quận 1\",\"source\":\"GPS\",\"latitude\":10.776,\"longitude\":106.701,\"accuracy\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("GPS"))
                .andExpect(jsonPath("$.latitude").value(10.776))
                .andExpect(jsonPath("$.longitude").value(106.701))
                .andExpect(jsonPath("$.accuracy").value(25))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mvc.perform(get("/api/locations/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Bến Nghé, Quận 1"))
                .andExpect(jsonPath("$.latitude").value(10.776));

        mvc.perform(delete("/api/locations/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        Matcher matcher = TOKEN.matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) throw new AssertionError("Access token not found");
        return matcher.group(1);
    }

    private String authBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
