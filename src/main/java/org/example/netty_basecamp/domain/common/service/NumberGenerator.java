package org.example.netty_basecamp.domain.common.service;

import org.example.netty_basecamp.domain.coupon.domain.Coupon;

public interface NumberGenerator {
    String generate(Coupon coupon);
}
