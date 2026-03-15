package org.example.netty_basecamp.domains.fare.domain.calculation.strategies;

import org.example.netty_basecamp.domains.common.vo.Money;
import org.example.netty_basecamp.domains.fare.domain.calculation.FareCalculationContext;
import org.example.netty_basecamp.domains.fare.domain.calculation.FarePolicyStrategy;
import org.example.netty_basecamp.domains.fare.domain.policy.CalculationBasisEnum;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicyTypeEnum;

public class WeekendSurchargeStrategy implements FarePolicyStrategy {

    @Override
    public FarePolicyTypeEnum getType() {
        return FarePolicyTypeEnum.WEEKEND_SURCHARGE;
    }

    @Override
    public FareCalculationContext apply(FareCalculationContext context, FarePolicy policy) {
        // 단리/복리에 따라 기준 금액 결정
        Money baseAmount = (policy.getBasis() == CalculationBasisEnum.ORIGINAL)
                ? context.getOriginalPrice()
                : context.getCurrentPrice();

        // policy.getValue()가 20이면 20% 할증
        Money surcharge = baseAmount.multiplyPercent(policy.getValue());
        Money newPrice = context.getCurrentPrice().add(surcharge);

        return context.applyPolicy(newPrice,
                "주말 할증 " + policy.getValue() + "% 적용");
    }
}