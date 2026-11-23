package com.oneforall.padobackend.service;

import com.oneforall.padobackend.dto.CreateReportRequest;
import com.oneforall.padobackend.dto.ReportResponse;
import com.oneforall.padobackend.entity.AuditEventType;
import com.oneforall.padobackend.entity.AuditTargetType;
import com.oneforall.padobackend.entity.ChatConversation;
import com.oneforall.padobackend.entity.Product;
import com.oneforall.padobackend.entity.PurchaseOrder;
import com.oneforall.padobackend.entity.Report;
import com.oneforall.padobackend.entity.ReportStatus;
import com.oneforall.padobackend.entity.ReportTargetType;
import com.oneforall.padobackend.entity.User;
import com.oneforall.padobackend.exception.BadRequestException;
import com.oneforall.padobackend.exception.ConflictException;
import com.oneforall.padobackend.exception.ForbiddenException;
import com.oneforall.padobackend.exception.ResourceNotFoundException;
import com.oneforall.padobackend.repository.ChatConversationRepository;
import com.oneforall.padobackend.repository.OrderRepository;
import com.oneforall.padobackend.repository.ProductRepository;
import com.oneforall.padobackend.repository.ReportRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private static final List<ReportStatus> ACTIVE_STATUSES =
            List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW);
    private static final long MAX_ACTIVE_REPORTS_PER_USER = 20;

    private final ReportRepository reports;
    private final UserRepository users;
    private final ProductRepository products;
    private final ChatConversationRepository conversations;
    private final OrderRepository orders;
    private final AuditService auditService;

    public ReportService(ReportRepository reports, UserRepository users, ProductRepository products,
                         ChatConversationRepository conversations, OrderRepository orders,
                         AuditService auditService) {
        this.reports = reports;
        this.users = users;
        this.products = products;
        this.conversations = conversations;
        this.orders = orders;
        this.auditService = auditService;
    }

    @Transactional
    public ReportResponse create(CreateReportRequest request, String reporterEmail) {
        User reporter = requireUser(reporterEmail);
        if (reports.countByReporterIdAndStatusIn(reporter.getId(), ACTIVE_STATUSES)
                >= MAX_ACTIVE_REPORTS_PER_USER) {
            throw new BadRequestException("You have too many active reports");
        }
        if (reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
                reporter.getId(), request.targetType(), request.targetId(), ACTIVE_STATUSES)) {
            throw new ConflictException("You already have an active report for this target");
        }

        TargetSnapshot target = resolveTarget(request.targetType(), request.targetId(), reporter);
        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setTargetLabel(target.label());
        report.setReportedUserId(target.reportedUserId());
        report.setReason(request.reason());
        report.setDetails(clean(request.details()));
        report.getEvidenceUrls().addAll(validateEvidence(request.evidenceUrls()));
        Report saved = reports.save(report);
        auditService.recordSuccess(AuditEventType.REPORT_CREATED, reporter,
                AuditTargetType.REPORT, saved.getId(), auditMetadata(saved));
        return ReportResponse.from(saved, false);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> mine(String reporterEmail) {
        User reporter = requireUser(reporterEmail);
        return reports.findByReporterIdOrderByCreatedAtDesc(reporter.getId()).stream()
                .map(report -> ReportResponse.from(report, false))
                .toList();
    }

    private TargetSnapshot resolveTarget(ReportTargetType type, Long id, User reporter) {
        return switch (type) {
            case PRODUCT -> productTarget(id, reporter);
            case USER -> userTarget(id, reporter);
            case CONVERSATION -> conversationTarget(id, reporter);
            case ORDER -> orderTarget(id, reporter);
        };
    }

    private TargetSnapshot productTarget(Long id, User reporter) {
        Product product = products.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getOwner().getId().equals(reporter.getId())) {
            throw new ConflictException("You cannot report your own product");
        }
        return new TargetSnapshot(product.getTitle(), product.getOwner().getId());
    }

    private TargetSnapshot userTarget(Long id, User reporter) {
        User target = users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (target.getId().equals(reporter.getId())) {
            throw new ConflictException("You cannot report yourself");
        }
        return new TargetSnapshot(target.getEmail(), target.getId());
    }

    private TargetSnapshot conversationTarget(Long id, User reporter) {
        ChatConversation conversation = conversations.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        boolean buyer = conversation.getBuyer().getId().equals(reporter.getId());
        boolean seller = conversation.getSeller().getId().equals(reporter.getId());
        if (!buyer && !seller) throw new ForbiddenException("You cannot report this conversation");
        User reported = buyer ? conversation.getSeller() : conversation.getBuyer();
        return new TargetSnapshot("Conversation about " + conversation.getProduct().getTitle(), reported.getId());
    }

    private TargetSnapshot orderTarget(Long id, User reporter) {
        PurchaseOrder order = orders.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        boolean buyer = order.getBuyerId().equals(reporter.getId());
        boolean seller = order.getSellerId().equals(reporter.getId());
        if (!buyer && !seller) throw new ForbiddenException("You cannot report this order");
        return new TargetSnapshot("Order " + order.getId() + " - " + order.getProductTitle(),
                buyer ? order.getSellerId() : order.getBuyerId());
    }

    private List<String> validateEvidence(List<String> evidenceUrls) {
        if (evidenceUrls == null) return List.of();
        return evidenceUrls.stream().map(String::trim).filter(value -> !value.isEmpty()).map(value -> {
            try {
                URI uri = URI.create(value);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) {
                    throw new IllegalArgumentException();
                }
                return value;
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Evidence URLs must be valid HTTP(S) URLs");
            }
        }).distinct().toList();
    }

    private User requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> auditMetadata(Report report) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("targetType", report.getTargetType().name());
        metadata.put("targetId", report.getTargetId());
        metadata.put("reason", report.getReason().name());
        metadata.put("status", report.getStatus().name());
        if (report.getReportedUserId() != null) metadata.put("reportedUserId", report.getReportedUserId());
        return metadata;
    }

    private record TargetSnapshot(String label, Long reportedUserId) {}
}
