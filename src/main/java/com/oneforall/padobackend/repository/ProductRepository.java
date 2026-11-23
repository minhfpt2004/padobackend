package com.oneforall.padobackend.repository;

import com.oneforall.padobackend.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths = "owner")
    List<Product> findAllByModerationHiddenFalseOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "owner")
    List<Product> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"owner", "images", "deliveryOptions"})
    @Query("select distinct p from Product p where p.id = :id and p.moderationHidden = false")
    Optional<Product> findVisibleDetailById(@Param("id") Long id);

    boolean existsByOwnerIdAndTitleIgnoreCase(Long ownerId, String title);
    Optional<Product> findByOwnerIdAndTitleIgnoreCase(Long ownerId, String title);
    void deleteAllByOwnerId(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p join fetch p.owner where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
