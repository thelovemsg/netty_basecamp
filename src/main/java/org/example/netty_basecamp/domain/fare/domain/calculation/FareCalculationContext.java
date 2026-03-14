package org.example.netty_basecamp.domain.fare.domain.calculation;

import org.example.netty_basecamp.domain.common.vo.Money;
import org.example.netty_basecamp.domain.fare.domain.Fare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FareCalculationContext {

    private final Fare originFare;
    private final Money originalPrice;    // 최초 기준가 (단리 계산용, 불변)
    private final Money currentPrice;     // 현재까지 계산된 금액
    private final List<String> appliedPolicyDescriptions;  // 적용 이력

    private FareCalculationContext(Fare originFare, Money originalPrice,
                                   Money currentPrice, List<String> appliedPolicyDescriptions) {
        this.originFare = originFare;
        this.originalPrice = originalPrice;
        this.currentPrice = currentPrice;
        this.appliedPolicyDescriptions = Collections.unmodifiableList(appliedPolicyDescriptions);
    }

    // 파이프라인 시작점
    public static FareCalculationContext init(Fare fare) {
        return new FareCalculationContext(
                fare,
                fare.getBasePrice(),
                fare.getBasePrice(),
                new ArrayList<>()
        );
    }

    // 정책 하나 적용 후 새로운 컨텍스트 반환 (불변 유지)
    public FareCalculationContext applyPolicy(Money newPrice, String description) {
        List<String> newDescriptions = new ArrayList<>(this.appliedPolicyDescriptions);
        newDescriptions.add(description);

        return new FareCalculationContext(
                this.originFare,
                this.originalPrice,
                newPrice,
                newDescriptions
        );
    }

    public Fare getOriginFare() { return originFare; }
    public Money getOriginalPrice() { return originalPrice; }
    public Money getCurrentPrice() { return currentPrice; }
    public List<String> getAppliedPolicyDescriptions() { return appliedPolicyDescriptions; }

}