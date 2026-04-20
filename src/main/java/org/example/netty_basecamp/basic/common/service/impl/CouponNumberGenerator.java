package org.example.netty_basecamp.basic.common.service.impl;

import org.example.netty_basecamp.basic.common.service.NumberGenerator;
import org.example.netty_basecamp.basic.coupon.domain.Coupon;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class CouponNumberGenerator implements NumberGenerator {

    private static final DateTimeFormatter dateTimeFormatter= DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public String generate(Coupon coupon) {
        String date = LocalDate.now().format(dateTimeFormatter);
        String couponId = String.format("%06d", coupon.getId());
        String random = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        return date + "-" + couponId + "-" + random;
    }
}