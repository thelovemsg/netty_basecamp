package org.example.netty_basecamp.domain.coupon.application;

import org.example.netty_basecamp.domain.common.vo.Money;
import org.example.netty_basecamp.domain.coupon.domain.Coupon;
import org.example.netty_basecamp.domain.coupon.domain.IssuedCoupon;
import org.example.netty_basecamp.domain.coupon.domain.service.CouponIssueDomainService;
import org.example.netty_basecamp.domain.coupon.infrastructure.CouponRepository;
import org.example.netty_basecamp.domain.fare.domain.Fare;
import org.example.netty_basecamp.domain.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.domain.fare.domain.service.FareCalculationDomainService;
import org.example.netty_basecamp.domain.fare.infrastructure.FarePolicyRepository;
import org.example.netty_basecamp.domain.fare.infrastructure.FareRepository;

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

    public IssuedCoupon issueCoupon(Long fareId, Long couponId, Long memberId) {
        // 인프라에서 꺼내고
        Fare fare = fareRepository.findById(fareId);
        List<FarePolicy> policies = farePolicyRepository.findByFareId(fareId);
        Coupon coupon = couponRepository.findById(couponId);

        // 도메인한테 시키고
        Money finalPrice = fareCalculationDomainService.calculateFinalPrice(fare, policies);

        // 도메인한테 시키고
        return couponIssueDomainService.issueToMember(coupon, memberId, finalPrice);
    }
}