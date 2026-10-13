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
    private static final List<OrderStatus> BUYER_ACTIVE_ORDER_STATUSES =
            List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED);

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
        if (orders.existsByProductIdAndBuyerIdAndStatusIn(
                product.getId(), buyer.getId(), BUYER_ACTIVE_ORDER_STATUSES)) {
            throw new ConflictException("You already have an active order for this product");
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
        validateBuyerCoordinates(request.buyerLatitude(), request.buyerLongitude());

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
        order.setBuyerLatitude(request.buyerLatitude());
        order.setBuyerLongitude(request.buyerLongitude());
        if (request.buyerLatitude() != null
                && product.getLatitude() != null
                && product.getLongitude() != null) {
            order.setDistanceKm(distanceKm(
                    product.getLatitude(), product.getLongitude(),
                    request.buyerLatitude(), request.buyerLongitude()));
        }

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
        if (target == OrderStatus.CONFIRMED) {
            return confirm(id, actor);
        }
        PurchaseOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        verifyParticipantOrAdmin(order, actor);

        if (order.getStatus() == target) {
            return OrderResponse.from(order);
        }

        switch (target) {
            case CONFIRMED -> throw new IllegalStateException("Confirmation is handled separately");
            case COMPLETED -> complete(order, actor);
            case CANCELLED -> cancel(order);
            case PENDING -> throw new ConflictException("Order cannot return to pending status");
        }

        return OrderResponse.from(orders.save(order));
    }

    private OrderResponse confirm(Long id, User actor) {
        PurchaseOrder snapshot = requireOrder(id);
        verifyParticipantOrAdmin(snapshot, actor);
        if (snapshot.getStatus() == OrderStatus.CONFIRMED) {
            return OrderResponse.from(snapshot);
        }
        if (!isAdmin(actor) && !snapshot.getSellerId().equals(actor.getId())) {
            throw new ForbiddenException("Only the seller can confirm this order");
        }

        Product product = products.findByIdForUpdate(snapshot.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        PurchaseOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw invalidTransition(order, OrderStatus.CONFIRMED);
        }
        if (product.isSold()) {
            throw new ConflictException("Another buyer has already been selected for this product");
        }

        Instant now = Instant.now();
        product.setSold(true);
        order.setStatus(OrderStatus.CONFIRMED);
        List<PurchaseOrder> otherCandidates = orders
                .findByProductIdAndStatusAndIdNotOrderByCreatedAtAsc(
                        order.getProductId(), OrderStatus.PENDING, order.getId());
        otherCandidates.forEach(candidate -> {
            candidate.setStatus(OrderStatus.CANCELLED);
            candidate.setCancelledAt(now);
        });
        orders.saveAll(otherCandidates);
        return OrderResponse.from(orders.save(order));
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
        boolean releasesProduct = order.getStatus() == OrderStatus.CONFIRMED;
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        if (releasesProduct) {
            products.findByIdForUpdate(order.getProductId()).ifPresent(product -> product.setSold(false));
        }
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

    private void validateBuyerCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new BadRequestException("Buyer location requires both latitude and longitude or neither");
        }
        if (latitude != null && (!Double.isFinite(latitude) || !Double.isFinite(longitude))) {
            throw new BadRequestException("Invalid buyer location coordinates");
        }
    }

    private double distanceKm(double latitudeA, double longitudeA, double latitudeB, double longitudeB) {
        double latitudeDelta = Math.toRadians(latitudeB - latitudeA);
        double longitudeDelta = Math.toRadians(longitudeB - longitudeA);
        double value = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitudeA)) * Math.cos(Math.toRadians(latitudeB))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double distance = 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, value))));
        return Math.round(distance * 100.0) / 100.0;
    }
}
