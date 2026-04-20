package org.example.netty_basecamp.basic.calculation;

import org.example.netty_basecamp.basic.common.vo.Money;
import org.example.netty_basecamp.basic.fare.domain.Fare;
import org.example.netty_basecamp.basic.fare.domain.FareStatusEnum;
import org.example.netty_basecamp.basic.fare.domain.FareTypeEnum;
import org.example.netty_basecamp.basic.fare.domain.calculation.FareCalculationContext;
import org.example.netty_basecamp.basic.fare.domain.calculation.FareCalculationPipeline;
import org.example.netty_basecamp.basic.fare.domain.calculation.FarePolicyStrategyRegistry;
import org.example.netty_basecamp.basic.fare.domain.calculation.strategies.FixedAmountDiscountStrategy;
import org.example.netty_basecamp.basic.fare.domain.calculation.strategies.LongStayDiscountStrategy;
import org.example.netty_basecamp.basic.fare.domain.calculation.strategies.WeekendSurchargeStrategy;
import org.example.netty_basecamp.basic.fare.domain.policy.CalculationBasisEnum;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicyTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FareCalculationPipelineTest {

    private FareCalculationPipeline pipeline;
    private Fare fare;

    @BeforeEach
    void setUp() {
        FarePolicyStrategyRegistry registry = new FarePolicyStrategyRegistry(
                List.of(
                        new WeekendSurchargeStrategy(),
                        new LongStayDiscountStrategy(),
                        new FixedAmountDiscountStrategy()
                )
        );
        pipeline = new FareCalculationPipeline(registry);

        fare = Fare.builder()
                .id(1L)
                .name("스탠다드 룸")
                .basePrice(Money.of(100000))
                .status(FareStatusEnum.ACTIVE)
                .fareType(FareTypeEnum.A_TYPE)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();
    }

    @Test
    @DisplayName("정책이 없으면 기본 요금이 그대로 반환된다")
    void 정책_없음() {
        FareCalculationContext result = pipeline.calculate(fare, List.of());

        assertThat(result.getCurrentPrice()).isEqualTo(Money.of(100000));
    }

    @Test
    @DisplayName("단일 정책이 적용된다")
    void 단일_정책_적용() {
        List<FarePolicy> policies = List.of(
                FarePolicy.builder()
                        .id(1L)
                        .fareId(1L)
                        .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                        .value(new BigDecimal("20"))
                        .basis(CalculationBasisEnum.ORIGINAL)
                        .priority(1)
                        .createdAt(1000L)
                        .modifiedAt(1000L)
                        .build()
        );

        FareCalculationContext result = pipeline.calculate(fare, policies);

        assertThat(result.getCurrentPrice()).isEqualTo(Money.of(120000));
    }

    @Test
    @DisplayName("여러 정책이 priority 순서대로 적용된다")
    void 다중_정책_priority_순서() {
        List<FarePolicy> policies = List.of(
                FarePolicy.builder()
                        .id(2L)
                        .fareId(1L)
                        .type(FarePolicyTypeEnum.LONG_STAY_DISCOUNT)
                        .value(new BigDecimal("10"))
                        .basis(CalculationBasisEnum.ORIGINAL)
                        .priority(2)
                        .createdAt(1000L)
                        .modifiedAt(1000L)
                        .build(),
                FarePolicy.builder()
                        .id(1L)
                        .fareId(1L)
                        .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                        .value(new BigDecimal("20"))
                        .basis(CalculationBasisEnum.ORIGINAL)
                        .priority(1)
                        .createdAt(1000L)
                        .modifiedAt(1000L)
                        .build()
        );

        FareCalculationContext result = pipeline.calculate(fare, policies);

        // 단리: 10만 + 2만(10만의 20%) - 1만(10만의 10%) = 11만
        assertThat(result.getCurrentPrice()).isEqualTo(Money.of(110000));
    }

    @Test
    @DisplayName("복리 기준으로 계산하면 누적 금액 기준으로 적용된다")
    void 복리_계산() {
        List<FarePolicy> policies = List.of(
                FarePolicy.builder()
                        .id(1L)
                        .fareId(1L)
                        .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                        .value(new BigDecimal("20"))
                        .basis(CalculationBasisEnum.ORIGINAL)
                        .priority(1)
                        .createdAt(1000L)
                        .modifiedAt(1000L)
                        .build(),
                FarePolicy.builder()
                        .id(2L)
                        .fareId(1L)
                        .type(FarePolicyTypeEnum.LONG_STAY_DISCOUNT)
                        .value(new BigDecimal("10"))
                        .basis(CalculationBasisEnum.ACCUMULATED)
                        .priority(2)
                        .createdAt(1000L)
                        .modifiedAt(1000L)
                        .build()
        );

        FareCalculationContext result = pipeline.calculate(fare, policies);

        // 10만 + 2만(10만의 20%) = 12만 → 12만 - 1만2천(12만의 10%) = 10만8천
        assertThat(result.getCurrentPrice()).isEqualTo(Money.of(108000));
    }
}