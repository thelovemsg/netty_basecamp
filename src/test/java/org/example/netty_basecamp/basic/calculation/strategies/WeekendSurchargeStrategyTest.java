package org.example.netty_basecamp.basic.calculation.strategies;

import org.example.netty_basecamp.basic.common.vo.Money;
import org.example.netty_basecamp.basic.fare.domain.Fare;
import org.example.netty_basecamp.basic.fare.domain.FareStatusEnum;
import org.example.netty_basecamp.basic.fare.domain.FareTypeEnum;
import org.example.netty_basecamp.basic.fare.domain.calculation.FareCalculationContext;
import org.example.netty_basecamp.basic.fare.domain.calculation.strategies.WeekendSurchargeStrategy;
import org.example.netty_basecamp.basic.fare.domain.policy.CalculationBasisEnum;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicyTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WeekendSurchargeStrategyTest {
    private WeekendSurchargeStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new WeekendSurchargeStrategy();
    }

    @Test
    @DisplayName("단리 기준으로 할증 금액을 계산한다")
    void 단리_할증_계산() {
        // given
        Fare fare = Fare.builder()
                .id(1L)
                .name("스탠다드 룸")
                .basePrice(Money.of(100000))
                .status(FareStatusEnum.ACTIVE)
                .fareType(FareTypeEnum.A_TYPE)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        FareCalculationContext context = FareCalculationContext.init(fare);

        FarePolicy policy = FarePolicy.builder()
                .id(1L)
                .fareId(1L)
                .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                .value(new BigDecimal("20"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when
        FareCalculationContext result = strategy.apply(context, policy);

        // then
        assertThat(result.getCurrentPrice()).isEqualTo(Money.of(120000));
        assertThat(result.getOriginalPrice()).isEqualTo(Money.of(100000));
    }
}