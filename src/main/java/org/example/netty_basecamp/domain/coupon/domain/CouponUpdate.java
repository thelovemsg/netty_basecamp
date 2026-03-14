package org.example.netty_basecamp.domain.coupon.domain;

import org.example.netty_basecamp.domain.common.vo.Money;

import java.time.LocalDate;

public record CouponUpdate(Long id, String description, LocalDate expireDate, Money originalPrice, int usedCount,
                           int totalCount) {

    public static CouponUpdateBuilder builder() {
        return new CouponUpdateBuilder();
    }

    public static class CouponUpdateBuilder {
        private Long id;
        private String description;
        private LocalDate expireDate;
        private Money originalPrice;
        private int usedCount;
        private int totalCount;

        CouponUpdateBuilder() {
        }

        public CouponUpdateBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CouponUpdateBuilder description(String description) {
            this.description = description;
            return this;
        }

        public CouponUpdateBuilder expireDate(LocalDate expireDate) {
            this.expireDate = expireDate;
            return this;
        }

        public CouponUpdateBuilder originalPrice(Money originalPrice) {
            this.originalPrice = originalPrice;
            return this;
        }

        public CouponUpdateBuilder usedCount(int usedCount) {
            this.usedCount = usedCount;
            return this;
        }

        public CouponUpdateBuilder totalCount(int totalCount) {
            this.totalCount = totalCount;
            return this;
        }

        public CouponUpdate build() {
            return new CouponUpdate(id, description, expireDate, originalPrice, usedCount, totalCount);
        }
    }
}
