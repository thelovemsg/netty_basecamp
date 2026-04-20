package org.example.netty_basecamp.basic.coupon.domain;

public interface CouponRepository {
    Coupon findById(Long couponId);
    void save(Coupon coupon);
}
