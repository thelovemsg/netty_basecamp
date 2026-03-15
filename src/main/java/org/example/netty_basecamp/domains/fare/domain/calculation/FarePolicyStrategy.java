package org.example.netty_basecamp.domains.fare.domain.calculation;

import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicyTypeEnum;

public interface FarePolicyStrategy {

    // 이 전략이 어떤 타입을 처리하는지
    FarePolicyTypeEnum getType();

    // 정책 적용
    FareCalculationContext apply(FareCalculationContext context, FarePolicy policy);
}