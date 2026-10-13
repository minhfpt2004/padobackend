package com.oneforall.padobackend.controller;

import com.oneforall.padobackend.dto.UserResponse;
import com.oneforall.padobackend.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    List<UserResponse> listUsers() {
        return adminService.listUsers();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        adminService.deleteUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
