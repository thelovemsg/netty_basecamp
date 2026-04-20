package org.example.netty_basecamp.basic.calculation.strategies;

import org.example.netty_basecamp.basic.common.vo.Money;
import org.example.netty_basecamp.basic.fare.domain.Fare;
import org.example.netty_basecamp.basic.fare.domain.FareStatusEnum;
import org.example.netty_basecamp.basic.fare.domain.FareTypeEnum;
import org.example.netty_basecamp.basic.fare.domain.calculation.FareCalculationContext;
import org.example.netty_basecamp.basic.fare.domain.calculation.strategies.LongStayDiscountStrategy;
import org.example.netty_basecamp.basic.fare.domain.calculation.strategies.WeekendSurchargeStrategy;
import org.example.netty_basecamp.basic.fare.domain.policy.CalculationBasisEnum;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicyTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LongStayDiscountStrategyTest {

    private LongStayDiscountStrategy strategy;
    private FareCalculationContext context;

    @BeforeEach
    void setUp() {
        strategy = new LongStayDiscountStrategy();

        Fare fare = Fare.builder()
                .id(1L)
                .name("스탠다드 룸")
                .basePrice(Money.of(100000))
                .status(FareStatusEnum.ACTIVE)
                .fareType(FareTypeEnum.A_TYPE)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        context = FareCalculationContext.init(fare);
    }

    @Test
    @DisplayName("단리 기준으로 할인 금액을 계산한다")
    void 단리_할인_계산() {
        // given
        FarePolicy policy = FarePolicy.builder()
                .id(1L)
                .fareId(1L)
                .type(FarePolicyTypeEnum.LONG_STAY_DISCOUNT)
                .value(new BigDecimal("10"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when
        FareCalculationContext result = strategy.apply(context, policy);

        // then
        // 10만원 - 1만원(10만의 10%) = 9만원
        assertThat(result.getCurrentPrice()).isEqualTo(Money.of(90000));
        assertThat(result.getOriginalPrice()).isEqualTo(Money.of(100000));
    }

    @Test
    @DisplayName("복리 기준으로 할인 금액을 계산한다")
    void 복리_할인_계산() {
        // given - 이미 주말할증이 적용된 상태 시뮬레이션 (12만원)
        FarePolicy surchargePolicy = FarePolicy.builder()
                .id(1L)
                .fareId(1L)
                .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                .value(new BigDecimal("20"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        WeekendSurchargeStrategy surchargeStrategy = new WeekendSurchargeStrategy();
        FareCalculationContext afterSurcharge = surchargeStrategy.apply(context, surchargePolicy);
        // afterSurcharge.currentPrice = 12만원

        FarePolicy discountPolicy = FarePolicy.builder()
                .id(2L)
                .fareId(1L)
                .type(FarePolicyTypeEnum.LONG_STAY_DISCOUNT)
                .value(new BigDecimal("10"))
                .basis(CalculationBasisEnum.ACCUMULATED)
                .priority(2)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when
        FareCalculationContext result = strategy.apply(afterSurcharge, discountPolicy);

        // then
        // 12만원 - 1만2천원(12만의 10%) = 10만8천원
        assertThat(result.getCurrentPrice()).isEqualTo(Money.of(108000));
        assertThat(result.getOriginalPrice()).isEqualTo(Money.of(100000));
    }

    @Test
    @DisplayName("할인 적용 후 이력이 기록된다")
    void 할인_적용_이력() {
        // given
        FarePolicy policy = FarePolicy.builder()
                .id(1L)
                .fareId(1L)
                .type(FarePolicyTypeEnum.LONG_STAY_DISCOUNT)
                .value(new BigDecimal("10"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when
        FareCalculationContext result = strategy.apply(context, policy);

        // then
        assertThat(result.getAppliedPolicyDescriptions()).hasSize(1);
        assertThat(result.getAppliedPolicyDescriptions().get(0))
                .isEqualTo("장기 투숙 할인 10% 적용");
    }

    @Test
    @DisplayName("getType은 LONG_STAY_DISCOUNT를 반환한다")
    void 타입_확인() {
        assertThat(strategy.getType()).isEqualTo(FarePolicyTypeEnum.LONG_STAY_DISCOUNT);
    }
}