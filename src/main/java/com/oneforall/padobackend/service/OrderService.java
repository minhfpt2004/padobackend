package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.CreateOrderRequest;
import com.oneforall.padobackend.dto.OrderResponse;
import com.oneforall.padobackend.entity.DeliveryType;
import com.oneforall.padobackend.entity.OrderStatus;
import com.oneforall.padobackend.entity.Product;
import com.oneforall.padobackend.entity.PurchaseOrder;
import com.oneforall.padobackend.entity.Role;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.ConflictException;
import com.oneforall.padobackend.exception.ForbiddenException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.OrderRepository;
import com.oneforall.padobackend.repository.ProductRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class OrderService {
    private static final List<OrderStatus> BLOCKING_ORDER_STATUSES =
            List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.COMPLETED);

    private final OrderRepository orders;
    private final ProductRepository products;
    private final UserRepository users;

    public OrderService(OrderRepository orders, ProductRepository products, UserRepository users) {
        this.orders = orders;
        this.products = products;
        this.users = users;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String buyerEmail) {
        User buyer = requireUser(buyerEmail);
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getOwner().getId().equals(buyer.getId())) {
            throw new ConflictException("You cannot buy your own product");
        }
        if (product.isSold()) {
            throw new ConflictException("Product is no longer available");
        }
        if (orders.existsByProductIdAndStatusIn(product.getId(), BLOCKING_ORDER_STATUSES)) {
            throw new ConflictException("Product already has an active order");
        }
        if (!product.getDeliveryOptions().contains(request.deliveryType())) {
            throw new BadRequestException("Selected delivery method is not available for this product");
        }

        String deliveryAddress = clean(request.deliveryAddress());
        String meetupLocation = clean(request.meetupLocation());
        if (request.deliveryType() == DeliveryType.SHIP && deliveryAddress == null) {
            throw new BadRequestException("Delivery address is required for shipping");
        }
        if (request.deliveryType() == DeliveryType.MEETUP && meetupLocation == null) {
            throw new BadRequestException("Meetup location is required for direct meetup");
        }

        BigDecimal shippingFee = request.deliveryType() == DeliveryType.SHIP
                ? product.getShippingFee()
                : BigDecimal.ZERO;

        PurchaseOrder order = new PurchaseOrder();
        order.setProductId(product.getId());
        order.setBuyerId(buyer.getId());
        order.setSellerId(product.getOwner().getId());
        order.setBuyerEmail(buyer.getEmail());
        order.setSellerEmail(product.getOwner().getEmail());
        order.setProductTitle(product.getTitle());
        order.setProductImageUrl(product.getImages().isEmpty() ? null : product.getImages().getFirst());
        order.setItemPrice(product.getPrice());
        order.setShippingFee(shippingFee);
        order.setTotalAmount(product.getPrice().add(shippingFee));
        order.setDeliveryType(request.deliveryType());
        order.setStatus(OrderStatus.PENDING);
        order.setBuyerName(request.buyerName().trim());
        order.setBuyerPhone(request.buyerPhone().trim());
        order.setDeliveryAddress(deliveryAddress);
        order.setMeetupLocation(meetupLocation);
        order.setNote(clean(request.note()));

        return OrderResponse.from(orders.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findPurchases(String email) {
        User buyer = requireUser(email);
        return orders.findByBuyerIdOrderByCreatedAtDesc(buyer.getId()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findSales(String email) {
        User seller = requireUser(email);
        return orders.findBySellerIdOrderByCreatedAtDesc(seller.getId()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findPendingSales(String email) {
        User seller = requireUser(email);
        return orders.findBySellerIdAndStatusOrderByCreatedAtDesc(seller.getId(), OrderStatus.PENDING).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id, String email) {
        User actor = requireUser(email);
        PurchaseOrder order = requireOrder(id);
        verifyParticipantOrAdmin(order, actor);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus target, String email) {
        User actor = requireUser(email);
        PurchaseOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        verifyParticipantOrAdmin(order, actor);

        if (order.getStatus() == target) {
            return OrderResponse.from(order);
        }

        switch (target) {
            case CONFIRMED -> confirm(order, actor);
            case COMPLETED -> complete(order, actor);
            case CANCELLED -> cancel(order);
            case PENDING -> throw new ConflictException("Order cannot return to pending status");
        }

        return OrderResponse.from(orders.save(order));
    }

    private void confirm(PurchaseOrder order, User actor) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw invalidTransition(order, OrderStatus.CONFIRMED);
        }
        if (!isAdmin(actor) && !order.getSellerId().equals(actor.getId())) {
            throw new ForbiddenException("Only the seller can confirm this order");
        }
        Product product = products.findByIdForUpdate(order.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        product.setSold(true);
        order.setStatus(OrderStatus.CONFIRMED);
    }

    private void complete(PurchaseOrder order, User actor) {
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw invalidTransition(order, OrderStatus.COMPLETED);
        }
        if (!isAdmin(actor) && !order.getBuyerId().equals(actor.getId())) {
            throw new ForbiddenException("Only the buyer can confirm that the order was received");
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
    }

    private void cancel(PurchaseOrder order) {
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw invalidTransition(order, OrderStatus.CANCELLED);
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        products.findByIdForUpdate(order.getProductId()).ifPresent(product -> product.setSold(false));
    }

    private ConflictException invalidTransition(PurchaseOrder order, OrderStatus target) {
        return new ConflictException("Cannot change order status from " + order.getStatus() + " to " + target);
    }

    private PurchaseOrder requireOrder(Long id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private User requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void verifyParticipantOrAdmin(PurchaseOrder order, User actor) {
        if (!isAdmin(actor)
                && !order.getBuyerId().equals(actor.getId())
                && !order.getSellerId().equals(actor.getId())) {
            throw new ForbiddenException("You do not have access to this order");
        }
    }

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
