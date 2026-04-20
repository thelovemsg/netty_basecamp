package org.example.netty_basecamp.basic.fare.domain.service;

import org.example.netty_basecamp.basic.fare.domain.Fare;
import org.example.netty_basecamp.basic.fare.domain.calculation.FareCalculationContext;
import org.example.netty_basecamp.basic.fare.domain.calculation.FareCalculationPipeline;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicy;

import java.util.List;

public class FareCalculationDomainService {
    private final FareCalculationPipeline pipeline;

    public FareCalculationDomainService(FareCalculationPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public FareCalculationContext calculate(Fare fare, List<FarePolicy> policies) {
        return pipeline.calculate(fare, policies);
    }
}
