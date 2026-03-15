package org.example.netty_basecamp.domains.fare.domain.service;

import org.example.netty_basecamp.domains.common.vo.Money;
import org.example.netty_basecamp.domains.fare.domain.Fare;
import org.example.netty_basecamp.domains.fare.domain.calculation.FareCalculationContext;
import org.example.netty_basecamp.domains.fare.domain.calculation.FareCalculationPipeline;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicy;

import java.util.List;

public class FareCalculationDomainService {
    private final FareCalculationPipeline pipeline;

    public FareCalculationDomainService(FareCalculationPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public Money calculateFinalPrice(Fare fare, List<FarePolicy> policies) {
        FareCalculationContext result = pipeline.calculate(fare, policies);
        return result.getCurrentPrice();
    }
}
