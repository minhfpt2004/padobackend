package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.ProductRequest;
import com.oneforall.padobackend.dto.ProductResponse;
import com.oneforall.padobackend.entity.Product;
import com.oneforall.padobackend.entity.OrderStatus;
import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.exception.ConflictException;
import com.oneforall.padobackend.exception.ForbiddenException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.ProductRepository;
import com.oneforall.padobackend.repository.OrderRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {
    private static final List<OrderStatus> PROTECTED_ORDER_STATUSES =
            List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.COMPLETED);

    private final ProductRepository products;
    private final UserRepository users;
    private final OrderRepository orders;

    public ProductService(ProductRepository products, UserRepository users, OrderRepository orders) {
        this.products = products;
        this.users = users;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return products.findAllByOrderByCreatedAtDesc().stream().map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(requireProduct(id));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findMine(String email) {
        User user = requireUser(email);
        return products.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream().map(ProductResponse::from).toList();
    }

    @Transactional
    public ProductResponse create(ProductRequest request, String email) {
        Product product = new Product();
        product.setOwner(requireUser(email));
        apply(product, request);
        return ProductResponse.from(products.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request, String email) {
        Product product = requireProduct(id);
        verifyOwnerOrAdmin(product, requireUser(email));
        verifyNoProtectedOrder(product);
        apply(product, request);
        return ProductResponse.from(products.save(product));
    }

    @Transactional
    public void delete(Long id, String email) {
        Product product = requireProduct(id);
        verifyOwnerOrAdmin(product, requireUser(email));
        verifyNoProtectedOrder(product);
        products.delete(product);
    }

    private Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private User requireUser(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void verifyOwnerOrAdmin(Product product, User actor) {
        if (actor.getRole() != Role.ADMIN && !product.getOwner().getId().equals(actor.getId())) {
            throw new ForbiddenException("You can only modify your own products");
        }
    }

    private void verifyNoProtectedOrder(Product product) {
        if (orders.existsByProductIdAndStatusIn(product.getId(), PROTECTED_ORDER_STATUSES)) {
            throw new ConflictException(
                    "Product with an active or completed order cannot be modified or deleted");
        }
    }

    private void apply(Product product, ProductRequest request) {
        product.setTitle(request.title().trim());
        product.setBrand(request.brand().trim());
        product.setCategory(request.category().trim());
        product.setSize(request.size().trim());
        product.setColor(request.color().trim());
        product.setCondition(request.condition().trim());
        product.setPrice(request.price());
        product.setOriginalPrice(request.originalPrice());
        product.setShippingFee(request.shippingFee());
        product.setDescription(request.description().trim());
        product.setReason(request.reason() == null ? null : request.reason().trim());
        product.setSellerLocation(request.sellerLocation().trim());
        product.setLatitude(request.latitude());
        product.setLongitude(request.longitude());
        product.setSold(request.sold());
        product.getImages().clear();
        product.getImages().addAll(request.images());
        product.getDeliveryOptions().clear();
        product.getDeliveryOptions().addAll(request.deliveryOptions());
    }
}
