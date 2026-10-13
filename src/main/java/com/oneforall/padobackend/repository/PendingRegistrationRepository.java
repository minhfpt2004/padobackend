package com.oneforall.padobackend.repository;

import com.oneforall.padobackend.entity.PendingRegistration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registration from PendingRegistration registration where registration.email = :email")
    Optional<PendingRegistration> findLockedByEmail(@Param("email") String email);
}
