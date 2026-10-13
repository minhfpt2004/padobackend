package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.UserResponse;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.exception.ConflictException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.ProductRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {
    private final UserRepository users;
    private final ProductRepository products;

    public AdminService(UserRepository users, ProductRepository products) {
        this.users = users;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return users.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(UserResponse::from).toList();
    }

    @Transactional
    public void deleteUser(Long id, String adminEmail) {
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getEmail().equalsIgnoreCase(adminEmail)) throw new ConflictException("Admin cannot delete their own account");
        products.deleteAllByOwnerId(id);
        users.delete(user);
    }
}
