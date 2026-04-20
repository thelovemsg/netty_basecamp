package org.example.netty_basecamp.basic.common.service;

import org.example.netty_basecamp.basic.coupon.domain.Coupon;

public interface NumberGenerator {
    String generate(Coupon coupon);
}
