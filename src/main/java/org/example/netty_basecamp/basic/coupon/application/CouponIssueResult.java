package org.example.netty_basecamp.basic.coupon.application;

import org.example.netty_basecamp.basic.common.vo.Money;
import org.example.netty_basecamp.basic.coupon.domain.IssuedCoupon;

import java.util.List;

public record CouponIssueResult(
        IssuedCoupon issuedCoupon,
        Long fareId,
        String fareName,
        Money originalPrice,
        Money appliedPrice,
        List<String> appliedPolicyDescriptions
) {}
