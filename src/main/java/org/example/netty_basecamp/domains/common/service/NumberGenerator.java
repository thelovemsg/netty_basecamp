package org.example.netty_basecamp.domains.common.service;

import org.example.netty_basecamp.domains.coupon.domain.Coupon;

public interface NumberGenerator {
    String generate(Coupon coupon);
}
