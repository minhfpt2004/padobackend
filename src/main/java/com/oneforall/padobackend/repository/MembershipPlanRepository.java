package com.oneforall.padobackend.repository;

import com.oneforall.padobackend.entity.MembershipPlan;
import com.oneforall.padobackend.entity.MembershipPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, String> {
    List<MembershipPlan> findByStatusOrderByPriceAsc(MembershipPlanStatus status);
    List<MembershipPlan> findAllByOrderByPriceAsc();
}
