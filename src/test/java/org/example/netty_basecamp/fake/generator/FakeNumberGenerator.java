package org.example.netty_basecamp.fake.generator;

import org.example.netty_basecamp.domains.common.service.NumberGenerator;
import org.example.netty_basecamp.domains.coupon.domain.Coupon;

public class FakeNumberGenerator implements NumberGenerator {

    private final String couponNumber;

    public FakeNumberGenerator(String couponNumber) {
        this.couponNumber = couponNumber;
    }

    @Override
    public String generate(Coupon coupon) {
        return couponNumber;
    }
}
