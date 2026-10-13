package com.oneforall.padobackend.repository;

import com.oneforall.padobackend.entity.MembershipPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MembershipPaymentRepository extends JpaRepository<MembershipPayment, Long> {
    List<MembershipPayment> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<MembershipPayment> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MembershipPayment> findLockedByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from MembershipPayment payment where payment.id = :id")
    Optional<MembershipPayment> findLockedById(@Param("id") Long id);
}
