package org.example.netty_basecamp.domains.coupon.application;

import org.example.netty_basecamp.domains.common.vo.Money;
import org.example.netty_basecamp.domains.coupon.domain.Coupon;
import org.example.netty_basecamp.domains.coupon.domain.IssuedCoupon;
import org.example.netty_basecamp.domains.coupon.domain.service.CouponIssueDomainService;
import org.example.netty_basecamp.domains.coupon.domain.CouponRepository;
import org.example.netty_basecamp.domains.fare.domain.Fare;
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