package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.CreateOrderRequest;
import com.oneforall.padobackend.dto.OrderResponse;
import com.oneforall.padobackend.dto.UpdateOrderStatusRequest;
import com.oneforall.padobackend.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                         Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.create(request, authentication.getName()));
    }

    @GetMapping("/purchases")
    List<OrderResponse> purchases(Authentication authentication) {
        return orderService.findPurchases(authentication.getName());
    }

    @GetMapping("/sales")
    List<OrderResponse> sales(Authentication authentication) {
        return orderService.findSales(authentication.getName());
    }

    @GetMapping("/sales/pending")
    List<OrderResponse> pendingSales(Authentication authentication) {
        return orderService.findPendingSales(authentication.getName());
    }

    @GetMapping("/{id}")
    OrderResponse findById(@PathVariable Long id, Authentication authentication) {
        return orderService.findById(id, authentication.getName());
    }

    @PatchMapping("/{id}/status")
    OrderResponse updateStatus(@PathVariable Long id,
                               @Valid @RequestBody UpdateOrderStatusRequest request,
                               Authentication authentication) {
        return orderService.updateStatus(id, request.status(), authentication.getName());
    }
}
