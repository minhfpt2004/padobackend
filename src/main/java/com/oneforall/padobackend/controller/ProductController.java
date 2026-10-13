package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.ProductRequest;
import com.oneforall.padobackend.dto.ProductResponse;
import com.oneforall.padobackend.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    List<ProductResponse> findAll() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    List<ProductResponse> findMine(Authentication authentication) {
        return productService.findMine(authentication.getName());
    }

    @PostMapping
    ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request,
                           Authentication authentication) {
        return productService.update(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        productService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
