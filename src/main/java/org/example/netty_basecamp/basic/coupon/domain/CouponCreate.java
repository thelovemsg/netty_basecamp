package org.example.netty_basecamp.basic.coupon.domain;

import org.example.netty_basecamp.basic.common.vo.Money;

import java.time.LocalDate;

public record CouponCreate(String description, LocalDate expireDate, Money originalPrice, int totalCount,
                           int usedCount) {

    public static CouponCreateBuilder builder() {
        return new CouponCreateBuilder();
    }

    public static class CouponCreateBuilder {
        private String description;
        private LocalDate expireDate;
        private Money originalPrice;
        private int totalCount;
        private int usedCount;

        CouponCreateBuilder() {
        }

        public CouponCreateBuilder description(String description) {
            this.description = description;
            return this;
        }

        public CouponCreateBuilder expireDate(LocalDate expireDate) {
            this.expireDate = expireDate;
            return this;
        }

        public CouponCreateBuilder originalPrice(Money originalPrice) {
            this.originalPrice = originalPrice;
            return this;
        }

        public CouponCreateBuilder totalCount(int totalCount) {
            this.totalCount = totalCount;
            return this;
        }

        public CouponCreateBuilder usedCount(int usedCount) {
            this.usedCount = usedCount;
            return this;
        }

        public CouponCreate build() {
            return new CouponCreate(description, expireDate, originalPrice, totalCount, usedCount);
        }
    }
}