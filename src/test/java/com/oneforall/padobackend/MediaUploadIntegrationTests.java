package com.oneforall.padobackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MediaUploadIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    MockMvc mvc;

    @Test
    void imageUploadRequiresAuthenticationAndBackendCloudinaryConfiguration() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        mvc.perform(multipart("/api/uploads/images").file(image))
                .andExpect(status().isUnauthorized());

        String email = "media-" + UUID.randomUUID().toString().substring(0, 8) + "@pado.test";
        String authResponse = AuthTestHelper.registerAndVerify(mvc, email, "StrongPassword123!");
        Matcher matcher = TOKEN.matcher(authResponse);
        if (!matcher.find()) throw new AssertionError("Access token not found");

        mvc.perform(multipart("/api/uploads/images")
                        .file(image)
                        .header("Authorization", "Bearer " + matcher.group(1)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Cloudinary is not configured on the backend"));
    }
}
