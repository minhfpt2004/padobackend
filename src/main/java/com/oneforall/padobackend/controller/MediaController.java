package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.ImageUploadResponse;
import com.oneforall.padobackend.service.CloudinaryImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/uploads")
public class MediaController {
    private final CloudinaryImageService images;

    public MediaController(CloudinaryImageService images) {
        this.images = images;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<List<ImageUploadResponse>> uploadImages(@RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED).body(images.uploadImages(files));
    }
}
