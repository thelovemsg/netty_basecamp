package org.example.netty_basecamp.basic.fare.domain.calculation;

import org.example.netty_basecamp.basic.fare.domain.Fare;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicy;

import java.util.Comparator;
import java.util.List;

public class FareCalculationPipeline {

    private final FarePolicyStrategyRegistry registry;

    public FareCalculationPipeline(FarePolicyStrategyRegistry registry) {
        this.registry = registry;
    }

    public FareCalculationContext calculate(Fare fare, List<FarePolicy> policies) {
        FareCalculationContext context = FareCalculationContext.init(fare);

        List<FarePolicy> sorted = policies.stream()
                .sorted(Comparator.comparingInt(FarePolicy::getPriority))
                .toList();

        for (FarePolicy policy : sorted) {
            FarePolicyStrategy strategy = registry.resolve(policy.getType());
            context = strategy.apply(context, policy);
        }

        return context;
    }
}