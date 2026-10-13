package com.oneforall.padobackend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oneforall.padobackend.dto.ImageUploadResponse;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.MediaStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class CloudinaryImageService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    private final RestClient cloudinary;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;
    private final int maxFiles;
    private final long maxFileBytes;

    public CloudinaryImageService(
            RestClient.Builder restClientBuilder,
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret,
            @Value("${app.cloudinary.folder:pado/products}") String folder,
            @Value("${app.cloudinary.max-files:8}") int maxFiles,
            @Value("${app.cloudinary.max-file-bytes:8388608}") long maxFileBytes
    ) {
        this.cloudinary = restClientBuilder.baseUrl("https://api.cloudinary.com/v1_1").build();
        this.cloudName = cloudName.trim();
        this.apiKey = apiKey.trim();
        this.apiSecret = apiSecret.trim();
        this.folder = folder.trim();
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileBytes;
    }

    public List<ImageUploadResponse> uploadImages(List<MultipartFile> files) {
        requireConfigured();
        if (files == null || files.isEmpty()) throw new BadRequestException("At least one image is required");
        if (files.size() > maxFiles) throw new BadRequestException("A maximum of " + maxFiles + " images is allowed");

        List<ImageUploadResponse> uploaded = new ArrayList<>();
        List<String> uploadedPublicIds = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                validate(file);
                CloudinaryUploadResult result = upload(file);
                ImageUploadResponse response = response(result);
                uploaded.add(response);
                uploadedPublicIds.add(response.publicId());
            }
            return uploaded;
        } catch (BadRequestException exception) {
            cleanup(uploadedPublicIds);
            throw exception;
        } catch (MediaStorageException exception) {
            cleanup(uploadedPublicIds);
            throw exception;
        } catch (IOException | RestClientException exception) {
            cleanup(uploadedPublicIds);
            throw new MediaStorageException("Could not upload images to Cloudinary", exception);
        }
    }

    private CloudinaryUploadResult upload(MultipartFile file) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(file.getBytes(), safeFilename(file)));
        body.add("folder", folder);
        body.add("allowed_formats", "jpg,jpeg,png,webp");
        body.add("use_filename", "true");
        body.add("unique_filename", "true");
        body.add("overwrite", "false");

        CloudinaryUploadResult result = cloudinary.post()
                .uri("/{cloudName}/image/upload", cloudName)
                .headers(headers -> headers.setBasicAuth(apiKey, apiSecret))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(CloudinaryUploadResult.class);
        if (result == null) throw new MediaStorageException("Cloudinary returned an empty upload response");
        return result;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Image files cannot be empty");
        if (file.getSize() > maxFileBytes) {
            throw new BadRequestException("Each image must be at most " + (maxFileBytes / 1024 / 1024) + " MB");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Only JPG, PNG and WebP images are supported");
        }
    }

    private ImageUploadResponse response(CloudinaryUploadResult result) {
        String url = result.secureUrl() == null ? "" : result.secureUrl();
        String publicId = result.publicId() == null ? "" : result.publicId();
        if (!url.startsWith("https://") || publicId.isBlank()) {
            throw new MediaStorageException("Cloudinary returned an invalid upload response");
        }
        return new ImageUploadResponse(url, publicId, result.format(), result.width(), result.height(), result.bytes());
    }

    private void cleanup(List<String> publicIds) {
        for (String publicId : publicIds) {
            try {
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("public_id", publicId);
                body.add("invalidate", "true");
                cloudinary.post()
                        .uri("/{cloudName}/image/destroy", cloudName)
                        .headers(headers -> headers.setBasicAuth(apiKey, apiSecret))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ignored) {
                // Preserve the original upload error. Cloudinary can be cleaned manually if rollback also fails.
            }
        }
    }

    private String safeFilename(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) return "product-image";
        return original.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void requireConfigured() {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new MediaStorageException("Cloudinary is not configured on the backend");
        }
        if (!cloudName.matches("[a-zA-Z0-9_-]+")) {
            throw new MediaStorageException("Cloudinary cloud name is invalid");
        }
    }

    private record CloudinaryUploadResult(
            @JsonProperty("secure_url") String secureUrl,
            @JsonProperty("public_id") String publicId,
            String format,
            int width,
            int height,
            long bytes
    ) {}

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
