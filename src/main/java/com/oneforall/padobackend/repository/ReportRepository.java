package com.oneforall.padobackend.repository;

import com.oneforall.padobackend.entity.Report;
import com.oneforall.padobackend.entity.ReportStatus;
import com.oneforall.padobackend.entity.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
            Long reporterId, ReportTargetType targetType, Long targetId, Collection<ReportStatus> statuses);

    long countByReporterIdAndStatusIn(Long reporterId, Collection<ReportStatus> statuses);

    @EntityGraph(attributePaths = {"reporter", "assignedAdmin", "evidenceUrls"})
    List<Report> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    @EntityGraph(attributePaths = {"reporter", "assignedAdmin", "evidenceUrls"})
    @Query("select report from Report report where report.id = :id")
    Optional<Report> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"reporter", "assignedAdmin"})
    @Query("""
            select report from Report report
            where (:status is null or report.status = :status)
              and (:targetType is null or report.targetType = :targetType)
              and (:reporterId is null or report.reporter.id = :reporterId)
            """)
    Page<Report> search(@Param("status") ReportStatus status,
                        @Param("targetType") ReportTargetType targetType,
                        @Param("reporterId") Long reporterId,
                        Pageable pageable);
}
