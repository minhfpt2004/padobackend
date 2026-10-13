package com.oneforall.padobackend.repository;

import com.oneforall.padobackend.entity.OrderStatus;
import com.oneforall.padobackend.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<PurchaseOrder> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    List<PurchaseOrder> findBySellerIdAndStatusOrderByCreatedAtDesc(Long sellerId, OrderStatus status);

    boolean existsByProductIdAndStatusIn(Long productId, Collection<OrderStatus> statuses);

    boolean existsByProductIdAndBuyerIdAndStatusIn(
            Long productId, Long buyerId, Collection<OrderStatus> statuses);

    List<PurchaseOrder> findByProductIdAndStatusAndIdNotOrderByCreatedAtAsc(
            Long productId, OrderStatus status, Long id);
    boolean existsByProductId(Long productId);

    boolean existsByProductIdAndBuyerIdAndSellerId(Long productId, Long buyerId, Long sellerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);
}
