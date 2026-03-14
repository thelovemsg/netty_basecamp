package org.example.netty_basecamp.domain.fare.domain.calculation.strategies;

import org.example.netty_basecamp.domain.common.vo.Money;
import org.example.netty_basecamp.domain.fare.domain.calculation.FareCalculationContext;
import org.example.netty_basecamp.domain.fare.domain.calculation.FarePolicyStrategy;
import org.example.netty_basecamp.domain.fare.domain.policy.CalculationBasisEnum;
import org.example.netty_basecamp.domain.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.domain.fare.domain.policy.FarePolicyTypeEnum;

public class LongStayDiscountStrategy implements FarePolicyStrategy {

    @Override
    public FarePolicyTypeEnum getType() {
        return FarePolicyTypeEnum.LONG_STAY_DISCOUNT;
    }

    @Override
    public FareCalculationContext apply(FareCalculationContext context, FarePolicy policy) {
        Money baseAmount = (policy.getBasis() == CalculationBasisEnum.ORIGINAL)
                ? context.getOriginalPrice()
                : context.getCurrentPrice();

        Money discount = baseAmount.multiplyPercent(policy.getValue());
        Money newPrice = context.getCurrentPrice().subtract(discount);

        return context.applyPolicy(newPrice,
                "장기 투숙 할인 " + policy.getValue() + "% 적용");
    }
}