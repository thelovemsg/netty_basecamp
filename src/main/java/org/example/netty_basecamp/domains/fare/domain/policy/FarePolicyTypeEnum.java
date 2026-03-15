package org.example.netty_basecamp.domains.fare.domain.policy;

public enum FarePolicyTypeEnum {
    WEEKEND_SURCHARGE(true),
    LONG_STAY_DISCOUNT(true),
    PEAK_SEASON_SURCHARGE(true),
    EARLY_BIRD_DISCOUNT(true),
    FIXED_AMOUNT_DISCOUNT(false);  // 고정 금액 할인

    private final boolean percentType;

    FarePolicyTypeEnum(boolean percentType) {
        this.percentType = percentType;
    }

    public boolean isPercentType() { return percentType; }
}