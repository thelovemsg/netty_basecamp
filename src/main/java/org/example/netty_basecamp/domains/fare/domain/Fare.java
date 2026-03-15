package org.example.netty_basecamp.domains.fare.domain;

import org.example.netty_basecamp.domains.common.vo.Money;

public class Fare {

    private final Long id;
    private final String name;
    private final Money basePrice;
    private final FareStatusEnum status;
    private final FareTypeEnum fareType;
    private final Long createdAt;
    private final Long modifiedAt;

    public Fare(Long id, String name, Money basePrice, FareStatusEnum status, FareTypeEnum fareType, Long createdAt, Long modifiedAt) {
        this.id = id;
        this.name = name;
        this.basePrice = basePrice;
        this.status = status;
        this.fareType = fareType;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    // 요금제 최초 생성
    public static Fare from(FareCreate fareCreate, long currentTime) {
        return Fare.builder()
                .name(fareCreate.name())
                .basePrice(fareCreate.basePrice())
                .status(FareStatusEnum.ACTIVE)
                .createdAt(currentTime)
                .modifiedAt(currentTime)
                .build();
    }

    // 요금제 기본 정보 업데이트 (이름 등)
    public Fare updateFareInfo(FareUpdate fareUpdate, long currentTime) {
        return Fare.builder()
                .id(this.id)
                .name(fareUpdate.name())
                .basePrice(this.basePrice)
                .status(this.status)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    // 요금 가격 변경
    public Fare changePrice(Money newPrice, long currentTime) {
        if (newPrice == null) {
            throw new IllegalArgumentException("변경할 요금은 필수입니다.");
        }

        return Fare.builder()
                .id(this.id)
                .name(this.name)
                .basePrice(newPrice)
                .status(this.status)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    // 요금제 판매 중단 (소프트 딜리트/상태 변경)
    public Fare deactivate(long currentTime) {
        if (this.status == FareStatusEnum.INACTIVE) {
            throw new IllegalStateException("이미 비활성화된 요금제입니다.");
        }

        return Fare.builder()
                .id(this.id)
                .name(this.name)
                .basePrice(this.basePrice)
                .status(FareStatusEnum.INACTIVE)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Money getBasePrice() { return basePrice; }
    public FareStatusEnum getStatus() { return status; }
    public FareTypeEnum getFareType() { return fareType; }
    public Long getCreatedAt() { return createdAt; }
    public Long getModifiedAt() { return modifiedAt; }

    public static FareBuilder builder() { return new FareBuilder(); }

    public static class FareBuilder {
        private Long id;
        private String name;
        private Money basePrice;
        private FareStatusEnum status;
        private FareTypeEnum fareType;
        private Long createdAt;
        private Long modifiedAt;

        FareBuilder() {}

        public FareBuilder id(Long id) { this.id = id; return this; }
        public FareBuilder name(String name) { this.name = name; return this; }
        public FareBuilder basePrice(Money basePrice) { this.basePrice = basePrice; return this; }
        public FareBuilder status(FareStatusEnum status) { this.status = status; return this; }
        public FareBuilder fareType(FareTypeEnum fareType) { this.fareType = fareType; return this; }
        public FareBuilder createdAt(Long createdAt) { this.createdAt = createdAt; return this; }
        public FareBuilder modifiedAt(Long modifiedAt) { this.modifiedAt = modifiedAt; return this; }

        public Fare build() {
            return new Fare(id, name, basePrice, status, fareType, createdAt, modifiedAt);
        }
    }

}