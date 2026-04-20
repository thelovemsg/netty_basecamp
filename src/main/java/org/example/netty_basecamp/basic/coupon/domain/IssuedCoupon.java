package org.example.netty_basecamp.basic.coupon.domain;

import org.example.netty_basecamp.basic.common.vo.Money;
import org.example.netty_basecamp.basic.coupon.domain.vo.IssuedCouponStatusEnum;

public class IssuedCoupon {

    private final Long id;
    private final Long couponId;
    private final Long memberId;
    private final String couponNumber;
    private final IssuedCouponStatusEnum status;
    private final Long issuedAt;
    private final Money appliedPrice;
    private final Long createdAt;
    private final Long modifiedAt;

    public IssuedCoupon(Long id, Long couponId, Long memberId, String couponNumber, IssuedCouponStatusEnum status, Long issuedAt, Money appliedPrice, Long createdAt, Long modifiedAt) {
        this.id = id;
        this.couponId = couponId;
        this.memberId = memberId;
        this.couponNumber = couponNumber;
        this.status = status;
        this.issuedAt = issuedAt;
        this.appliedPrice = appliedPrice;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    public static IssuedCoupon issue(Coupon coupon, Long memberId, String couponNumber, Long issuedAt, Money appliedPrice) {
        return IssuedCoupon.builder()
                .couponId(coupon.getId())
                .memberId(memberId)
                .couponNumber(couponNumber)
                .appliedPrice(appliedPrice)
                .status(IssuedCouponStatusEnum.UNUSED)
                .issuedAt(issuedAt)
                .createdAt(issuedAt)
                .modifiedAt(issuedAt)
                .build();
    }

    public IssuedCoupon use(long usingTime) {
        if (this.status != IssuedCouponStatusEnum.UNUSED) {
            throw new IllegalStateException("사용 가능한 쿠폰이 아닙니다.");
        }

        return IssuedCoupon.builder()
                .id(this.id)
                .couponId(this.couponId)
                .memberId(this.memberId)
                .couponNumber(this.couponNumber)
                .appliedPrice(this.appliedPrice)
                .status(IssuedCouponStatusEnum.USED)
                .issuedAt(this.issuedAt)
                .createdAt(this.createdAt)
                .modifiedAt(usingTime)
                .build();
    }

    public IssuedCoupon expire(long time) {
        if (this.status != IssuedCouponStatusEnum.UNUSED) {
            throw new IllegalStateException("이미 사용된 쿠폰은 만료할 수 없습니다.");
        }

        return IssuedCoupon.builder()
                .id(this.id)
                .couponId(this.couponId)
                .memberId(this.memberId)
                .appliedPrice(this.appliedPrice)
                .couponNumber(this.couponNumber)
                .status(IssuedCouponStatusEnum.EXPIRED)
                .issuedAt(this.issuedAt)
                .createdAt(this.createdAt)
                .modifiedAt(time)
                .build();
    }

    public Long getId() { return id; }
    public Long getCouponId() { return couponId; }
    public Long getMemberId() { return memberId; }
    public String getCouponNumber() { return couponNumber; }
    public IssuedCouponStatusEnum getStatus() { return status; }
    public Long getIssuedAt() { return issuedAt; }
    public Money getAppliedPrice() { return appliedPrice; }
    public Long getCreatedAt() { return createdAt; }
    public Long getModifiedAt() { return modifiedAt; }

    public static IssuedCouponBuilder builder() { return new IssuedCouponBuilder(); }

    public static class IssuedCouponBuilder {
        private Long id;
        private Long couponId;
        private Long memberId;
        private String couponNumber;
        private IssuedCouponStatusEnum status;
        private Long issuedAt;
        private Money appliedPrice;
        private Long createdAt;
        private Long modifiedAt;

        IssuedCouponBuilder() {}

        public IssuedCouponBuilder id(Long id) { this.id = id; return this; }
        public IssuedCouponBuilder couponId(Long couponId) { this.couponId = couponId; return this; }
        public IssuedCouponBuilder memberId(Long memberId) { this.memberId = memberId; return this; }
        public IssuedCouponBuilder couponNumber(String couponNumber) { this.couponNumber = couponNumber; return this; }
        public IssuedCouponBuilder status(IssuedCouponStatusEnum status) { this.status = status; return this; }
        public IssuedCouponBuilder issuedAt(Long issuedAt) { this.issuedAt = issuedAt; return this; }
        public IssuedCouponBuilder appliedPrice(Money appliedPrice) { this.appliedPrice = appliedPrice; return this; }
        public IssuedCouponBuilder createdAt(Long createdAt) { this.createdAt = createdAt; return this; }
        public IssuedCouponBuilder modifiedAt(Long modifiedAt) { this.modifiedAt = modifiedAt; return this; }

        public IssuedCoupon build() {
            return new IssuedCoupon(id, couponId, memberId, couponNumber, status, issuedAt, appliedPrice, createdAt, modifiedAt);
        }
    }
}