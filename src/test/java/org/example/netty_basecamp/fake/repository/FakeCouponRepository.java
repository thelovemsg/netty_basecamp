package org.example.netty_basecamp.fake.repository;

import org.example.netty_basecamp.domain.coupon.domain.Coupon;
import org.example.netty_basecamp.domain.coupon.infrastructure.CouponRepository;

import java.util.HashMap;
import java.util.Map;

public class FakeCouponRepository implements CouponRepository {
    private final Map<Long, Coupon> store = new HashMap<>();

    public void save(Coupon coupon) {
        store.put(coupon.getId(), coupon);
    }

    @Override
    public Coupon findById(Long id) {
        return store.get(id);
    }
}