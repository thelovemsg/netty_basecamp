package org.example.netty_basecamp.domain.coupon.infrastructure;

import org.example.netty_basecamp.domain.coupon.domain.Coupon;

public interface CouponRepository {
    Coupon findById(Long couponId);
    void save(Coupon coupon);
}
