package org.example.netty_basecamp.basic.fare.domain.calculation.strategies;

import org.example.netty_basecamp.basic.common.vo.Money;
import org.example.netty_basecamp.basic.fare.domain.calculation.FareCalculationContext;
import org.example.netty_basecamp.basic.fare.domain.calculation.FarePolicyStrategy;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicyTypeEnum;

import java.math.BigDecimal;

public class FixedAmountDiscountStrategy implements FarePolicyStrategy {

    @Override
    public FarePolicyTypeEnum getType() {
        return FarePolicyTypeEnum.FIXED_AMOUNT_DISCOUNT;
    }

    @Override
    public FareCalculationContext apply(FareCalculationContext context, FarePolicy policy) {
        // 단리/복리에 따라 기준 금액 결정
        Money targetAmount = context.getCurrentPrice();

        // policy.getValue()가 20이면 20% 할증
        BigDecimal value = policy.getValue();
        Money fixedAmount = Money.of(value);
        Money newPrice = targetAmount.subtract(fixedAmount);

        return context.applyPolicy(newPrice,"고정 할인 : " + policy.getValue() + "할인!");
    }
}
