package com.oneforall.padobackend;

import com.oneforall.padobackend.dto.ImageUploadResponse;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.MediaStorageException;
import com.oneforall.padobackend.service.CloudinaryImageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class CloudinaryImageServiceTests {
    @Test
    void uploadsAnImageWithBackendCredentialsAndMapsTheSecureUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CloudinaryImageService service = service(builder, "demo", "api-key", "api-secret");
        String basicAuth = "Basic " + HttpHeaders.encodeBasicAuth("api-key", "api-secret", StandardCharsets.UTF_8);

        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth))
                .andRespond(withSuccess("""
                        {"secure_url":"https://res.cloudinary.com/demo/image/upload/v1/pado/products/photo.jpg",
                         "public_id":"pado/products/photo","format":"jpg","width":1200,"height":1500,"bytes":245000}
                        """, MediaType.APPLICATION_JSON));

        List<ImageUploadResponse> uploaded = service.uploadImages(List.of(new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3}
        )));

        assertEquals(1, uploaded.size());
        assertEquals("https://res.cloudinary.com/demo/image/upload/v1/pado/products/photo.jpg", uploaded.getFirst().url());
        assertEquals("pado/products/photo", uploaded.getFirst().publicId());
        server.verify();
    }

    @Test
    void rejectsUnsupportedFilesBeforeCallingCloudinary() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CloudinaryImageService service = service(builder, "demo", "api-key", "api-secret");

        assertThrows(BadRequestException.class, () -> service.uploadImages(List.of(new MockMultipartFile(
                "files", "document.pdf", "application/pdf", new byte[]{1}
        ))));
        server.verify();
    }

    @Test
    void reportsMissingBackendConfiguration() {
        CloudinaryImageService service = service(RestClient.builder(), "", "", "");
        assertThrows(MediaStorageException.class, () -> service.uploadImages(List.of(new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", new byte[]{1}
        ))));
    }

    @Test
    void reportsApiKeyUsedAsApiSecretBeforeCallingCloudinary() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CloudinaryImageService service = service(builder, "demo", "same-value", "same-value");

        MediaStorageException exception = assertThrows(MediaStorageException.class,
                () -> service.uploadImages(List.of(new MockMultipartFile(
                        "files", "photo.jpg", "image/jpeg", new byte[]{1}
                ))));

        assertTrue(exception.getMessage().contains("API Secret, not the API Key"));
        server.verify();
    }

    @Test
    void reportsRejectedCloudinaryCredentialsClearly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CloudinaryImageService service = service(builder, "demo", "api-key", "wrong-secret");

        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid Signature\"}}"));

        MediaStorageException exception = assertThrows(MediaStorageException.class,
                () -> service.uploadImages(List.of(new MockMultipartFile(
                        "files", "photo.jpg", "image/jpeg", new byte[]{1}
                ))));

        assertTrue(exception.getMessage().contains("rejected the credentials"));
        server.verify();
    }

    private CloudinaryImageService service(RestClient.Builder builder, String cloud, String key, String secret) {
        return new CloudinaryImageService(builder, cloud, key, secret, "pado/products", 8, 8 * 1024 * 1024);
    }
}
