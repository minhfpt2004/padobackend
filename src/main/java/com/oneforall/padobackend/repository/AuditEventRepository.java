package com.oneforall.padobackend.repository;

import com.oneforall.padobackend.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>,
        JpaSpecificationExecutor<AuditEvent> {
    List<AuditEvent> findByActorEmailIgnoreCaseOrderByCreatedAtAsc(String actorEmail);
    List<AuditEvent> findByRequestIdOrderByIdAsc(String requestId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AuditEvent event where event.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
