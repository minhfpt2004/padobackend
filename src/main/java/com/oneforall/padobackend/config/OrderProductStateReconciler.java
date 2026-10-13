package com.oneforall.padobackend.config;

import com.oneforall.padobackend.entity.OrderStatus;
import com.oneforall.padobackend.entity.PurchaseOrder;
import com.oneforall.padobackend.repository.OrderRepository;
import com.oneforall.padobackend.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Component
public class OrderProductStateReconciler implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OrderProductStateReconciler.class);

    private final OrderRepository orders;
    private final ProductRepository products;

    public OrderProductStateReconciler(OrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<Long, ProductOrderState> states = new HashMap<>();
        for (PurchaseOrder order : orders.findAll()) {
            if (order.getStatus() == OrderStatus.PENDING
                    || order.getStatus() == OrderStatus.CONFIRMED
                    || order.getStatus() == OrderStatus.COMPLETED) {
                states.computeIfAbsent(order.getProductId(), ignored -> new ProductOrderState())
                        .accept(order.getStatus());
            }
        }

        int updated = 0;
        for (Map.Entry<Long, ProductOrderState> entry : states.entrySet()) {
            boolean shouldBeSold = entry.getValue().accepted;
            var product = products.findById(entry.getKey()).orElse(null);
            if (product != null && product.isSold() != shouldBeSold) {
                product.setSold(shouldBeSold);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Reconciled sold state for {} products from existing orders", updated);
        }
    }

    private static final class ProductOrderState {
        private boolean accepted;

        private void accept(OrderStatus status) {
            accepted = accepted || status == OrderStatus.CONFIRMED || status == OrderStatus.COMPLETED;
        }
    }
}
