package com.oneforall.padobackend.dto;

public record ImageUploadResponse(
        String url,
        String publicId,
        String format,
        int width,
        int height,
        long bytes
) {}
