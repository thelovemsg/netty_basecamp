package org.example.netty_basecamp.basic.fare.domain.policy;

public enum CalculationBasisEnum {
    ORIGINAL,    // 단리: 항상 Fare.basePrice 기준으로 계산
    ACCUMULATED  // 복리: 직전 정책 적용 후 금액 기준으로 계산
}