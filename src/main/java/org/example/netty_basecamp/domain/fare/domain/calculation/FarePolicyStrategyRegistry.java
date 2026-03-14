package org.example.netty_basecamp.domain.fare.domain.calculation;

import org.example.netty_basecamp.domain.fare.domain.policy.FarePolicyTypeEnum;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FarePolicyStrategyRegistry {

    private final Map<FarePolicyTypeEnum, FarePolicyStrategy> strategyMap;

    // 생성자에서 Strategy 리스트를 받아 Map으로 조립
    public FarePolicyStrategyRegistry(List<FarePolicyStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        FarePolicyStrategy::getType,
                        strategy -> strategy
                ));
    }

    public FarePolicyStrategy resolve(FarePolicyTypeEnum type) {
        FarePolicyStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("지원하지 않는 정책 타입입니다: " + type);
        }
        return strategy;
    }
}