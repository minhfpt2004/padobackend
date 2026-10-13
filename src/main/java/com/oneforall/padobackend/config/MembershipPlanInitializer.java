package com.oneforall.padobackend.config;

import com.oneforall.padobackend.entity.BillingCycle;
import com.oneforall.padobackend.entity.MembershipPlan;
import com.oneforall.padobackend.entity.MembershipPlanStatus;
import com.oneforall.padobackend.repository.MembershipPlanRepository;
import com.oneforall.padobackend.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@Order(0)
public class MembershipPlanInitializer implements ApplicationRunner {
    private final MembershipPlanRepository plans;
    private final UserRepository users;

    public MembershipPlanInitializer(MembershipPlanRepository plans, UserRepository users) {
        this.plans = plans;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensure("FREE", "FREE", "Dành cho người mới bắt đầu dọn tủ", "0",
                "Miễn phí vĩnh viễn", "Đăng bán, chat, giao dịch và profile cá nhân",
                BillingCycle.LIFETIME, 0, null, false, List.of(
                        "Đăng bán đồ pass không giới hạn",
                        "Chat trực tiếp giữa người mua và người bán",
                        "Giao dịch trực tiếp hoặc giao hàng"));
        ensure("PLUS", "PLUS", "Dành cho người thường xuyên pass đồ", "29000",
                "Tháng đầu 29K", "Ưu tiên hiển thị, 5 Boost/tháng và thống kê lượt xem",
                BillingCycle.MONTHLY, 5, "Phổ biến nhất", true, List.of(
                        "Tất cả quyền lợi của gói FREE",
                        "Ưu tiên hiển thị bài đăng",
                        "Tặng 5 lượt Boost khi kích hoạt",
                        "Thống kê lượt xem và lượt lưu"));
        ensure("PRO", "PRO", "Dành cho shop secondhand và người bán chuyên nghiệp", "79000",
                "Tháng đầu 79K", "Ưu tiên cao nhất, 15 Boost và công cụ analytics",
                BillingCycle.MONTHLY, 15, "Dành cho Shop", false, List.of(
                        "Tất cả quyền lợi của gói PLUS",
                        "Ưu tiên hiển thị cao nhất",
                        "Tặng 15 lượt Boost khi kích hoạt",
                        "Huy hiệu PRO Seller và analytics"));
        ensure("BOOST", "BOOST", "Combo đẩy bài lên top", "39000",
                "Combo 5 lượt", "Cộng 5 lượt Boost vào tài khoản",
                BillingCycle.PER_USE, 5, null, false, List.of(
                        "Cộng 5 lượt Boost vào tài khoản",
                        "Mỗi lượt dùng để đẩy một bài trong 24 giờ"));

        MembershipPlan free = plans.findById("FREE").orElseThrow();
        users.findAll().stream().filter(user -> user.getMembershipPlan() == null).forEach(user -> {
            user.setMembershipPlan(free);
            users.save(user);
        });
    }

    private void ensure(String code, String name, String tagline, String price, String discount,
                        String summary, BillingCycle cycle, int boostQuota, String badge,
                        boolean highlight, List<String> features) {
        if (plans.existsById(code)) return;
        MembershipPlan plan = new MembershipPlan();
        plan.setCode(code);
        plan.setName(name);
        plan.setTagline(tagline);
        plan.setPrice(new BigDecimal(price));
        plan.setFirstMonthDiscount(discount);
        plan.setBenefitsSummary(summary);
        plan.setBillingCycle(cycle);
        plan.setBoostQuota(boostQuota);
        plan.setBadge(badge);
        plan.setHighlight(highlight);
        plan.setStatus(MembershipPlanStatus.ACTIVE);
        plan.getFeatures().addAll(features);
        plans.save(plan);
    }
}
