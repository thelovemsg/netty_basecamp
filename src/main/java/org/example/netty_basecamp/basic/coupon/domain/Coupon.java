package org.example.netty_basecamp.basic.coupon.domain;

import org.example.netty_basecamp.basic.common.vo.Inventory;
import org.example.netty_basecamp.basic.common.vo.Money;

import java.time.LocalDate;

public class Coupon {

    private final Long id;
    private final String description;
    private final LocalDate expireDate;
    private final Inventory inventory;
    private final Money originalPrice;
    private final Long createdAt;
    private final Long modifiedAt;

    public Coupon(Long id, String description, LocalDate expireDate, Inventory inventory, Money originalPrice, Long createdAt, Long modifiedAt) {
        this.id = id;
        this.description = description;
        this.expireDate = expireDate;
        this.inventory = inventory;
        this.originalPrice = originalPrice;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    public static Coupon from(CouponCreate couponCreate, long currentTime) {
        Inventory inventory = Inventory.createInitial(couponCreate.totalCount());

        return Coupon.builder()
                .description(couponCreate.description())
                .inventory(inventory)
                .originalPrice(couponCreate.originalPrice())
                .expireDate(couponCreate.expireDate())
                .createdAt(currentTime)
                .modifiedAt(currentTime)
                .build();
    }

    public Coupon updateCouponInfo(CouponUpdate couponUpdate, long currentTime) {
        return Coupon.builder()
                .id(this.id)
                .description(couponUpdate.description())
                .inventory(this.inventory)
                .createdAt(this.createdAt)
                .expireDate(couponUpdate.expireDate())
                .modifiedAt(currentTime)
                .build();
    }

    public Coupon updateInventoryInfo(CouponUpdate couponUpdate, long currentTime) {

        Inventory inventory = Inventory.builder()
                .usedCount(couponUpdate.usedCount())
                .totalCount(couponUpdate.totalCount())
                .build();

        return Coupon.builder()
                .id(this.id)
                .description(this.description)
                .inventory(inventory)
                .expireDate(this.expireDate)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    public Coupon reserve(long currentTime) {
        if (this.expireDate.isBefore(LocalDate.now())) {
            throw new IllegalStateException("만료된 쿠폰입니다.");
        }

        Inventory usedInventory = this.inventory.use();

        return Coupon.builder()
                .id(this.id)
                .description(this.description)
                .inventory(usedInventory)
                .expireDate(this.expireDate)
                .originalPrice(this.originalPrice)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public LocalDate getExpireDate() { return expireDate; }
    public Inventory getInventory() { return inventory; }
    public Money getOriginalPrice() { return originalPrice; }
    public Long getCreatedAt() { return createdAt; }
    public Long getModifiedAt() { return modifiedAt; }

    public static CouponBuilder builder() { return new CouponBuilder(); }

    public static class CouponBuilder {
        private Long id;
        private String description;
        private LocalDate expireDate;
        private Inventory inventory;
        private Money originalPrice;
        private Long createdAt;
        private Long modifiedAt;

        CouponBuilder() {}

        public CouponBuilder id(Long id) { this.id = id; return this; }
        public CouponBuilder description(String description) { this.description = description; return this; }
        public CouponBuilder expireDate(LocalDate expireDate) { this.expireDate = expireDate; return this; }
        public CouponBuilder inventory(Inventory inventory) { this.inventory = inventory; return this; }
        public CouponBuilder originalPrice(Money originalPrice) { this.originalPrice = originalPrice; return this; }
        public CouponBuilder createdAt(Long createdAt) { this.createdAt = createdAt; return this; }
        public CouponBuilder modifiedAt(Long modifiedAt) { this.modifiedAt = modifiedAt; return this; }

        public Coupon build() {
            return new Coupon(id, description, expireDate, inventory, originalPrice, createdAt, modifiedAt);
        }
    }

}
