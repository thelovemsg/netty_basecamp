package org.example.netty_basecamp.domains.coupon.application;

import org.example.netty_basecamp.domains.coupon.domain.Coupon;
import org.example.netty_basecamp.domains.coupon.domain.IssuedCoupon;
import org.example.netty_basecamp.domains.coupon.domain.service.CouponIssueDomainService;
import org.example.netty_basecamp.domains.coupon.domain.CouponRepository;
import org.example.netty_basecamp.domains.fare.domain.Fare;
import org.example.netty_basecamp.domains.fare.domain.calculation.FareCalculationContext;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.domains.fare.domain.service.FareCalculationDomainService;
import org.example.netty_basecamp.domains.fare.domain.FarePolicyRepository;
import org.example.netty_basecamp.domains.fare.domain.FareRepository;

import java.util.List;

public class CouponIssueApplicationService {

    private final FareRepository fareRepository;
    private final FarePolicyRepository farePolicyRepository;
    private final CouponRepository couponRepository;
    private final FareCalculationDomainService fareCalculationDomainService;
    private final CouponIssueDomainService couponIssueDomainService;

    public CouponIssueApplicationService(FareRepository fareRepository, FarePolicyRepository farePolicyRepository,
                                         CouponRepository couponRepository, FareCalculationDomainService fareCalculationDomainService,
                                         CouponIssueDomainService couponIssueDomainService) {
        this.fareRepository = fareRepository;
        this.farePolicyRepository = farePolicyRepository;
        this.couponRepository = couponRepository;
        this.fareCalculationDomainService = fareCalculationDomainService;
        this.couponIssueDomainService = couponIssueDomainService;
    }

    public CouponIssueResult issueCoupon(Long fareId, Long couponId, Long memberId) {
        // 인프라에서 꺼내고
        Fare fare = fareRepository.findById(fareId);
        List<FarePolicy> policies = farePolicyRepository.findByFareId(fareId);
        Coupon coupon = couponRepository.findById(couponId);

        // 요금 계산 — 컨텍스트 전체 보존
        FareCalculationContext context = fareCalculationDomainService.calculate(fare, policies);

        // 쿠폰 발급
        IssuedCoupon issuedCoupon = couponIssueDomainService.issueToMember(coupon, memberId, context.getCurrentPrice());

        return new CouponIssueResult(
                issuedCoupon,
                fare.getId(),
                fare.getName(),
                context.getOriginalPrice(),
                context.getCurrentPrice(),
                context.getAppliedPolicyDescriptions()
        );
    }
}
