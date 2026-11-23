package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.UserResponse;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.exception.ConflictException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.ProductRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AdminService {
    private final UserRepository users;
    private final ProductRepository products;
    private final AuditService auditService;

    public AdminService(UserRepository users, ProductRepository products, AuditService auditService) {
        this.users = users;
        this.products = products;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return users.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(UserResponse::from).toList();
    }

    @Transactional
    public void deleteUser(Long id, String adminEmail) {
        User actor = users.findByEmailIgnoreCase(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getEmail().equalsIgnoreCase(adminEmail)) throw new ConflictException("Admin cannot delete their own account");
        int deletedProductCount = products.findByOwnerIdOrderByCreatedAtDesc(id).size();
        Map<String, Object> metadata = Map.of(
                "deletedUserEmail", user.getEmail(),
                "deletedUserRole", user.getRole().name(),
                "deletedProductCount", deletedProductCount
        );
        products.deleteAllByOwnerId(id);
        users.delete(user);
        auditService.recordSuccess(AuditEventType.ADMIN_USER_DELETED, actor,
                AuditTargetType.USER, id, metadata);
    }
}
