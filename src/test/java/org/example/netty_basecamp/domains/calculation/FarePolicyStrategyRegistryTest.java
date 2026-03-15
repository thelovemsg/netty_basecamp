package org.example.netty_basecamp.domains.calculation;

import org.example.netty_basecamp.domains.fare.domain.calculation.FarePolicyStrategy;
import org.example.netty_basecamp.domains.fare.domain.calculation.FarePolicyStrategyRegistry;
import org.example.netty_basecamp.domains.fare.domain.calculation.strategies.LongStayDiscountStrategy;
import org.example.netty_basecamp.domains.fare.domain.calculation.strategies.WeekendSurchargeStrategy;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicyTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FarePolicyStrategyRegistryTest {

    @Test
    @DisplayName("타입에 맞는 Strategy를 반환한다")
    void 타입으로_Strategy_조회() {
        // given
        FarePolicyStrategyRegistry registry = new FarePolicyStrategyRegistry(
                List.of(new WeekendSurchargeStrategy(), new LongStayDiscountStrategy())
        );

        // when
        FarePolicyStrategy strategy = registry.resolve(FarePolicyTypeEnum.WEEKEND_SURCHARGE);

        // then
        assertThat(strategy).isInstanceOf(WeekendSurchargeStrategy.class);
    }

    @Test
    @DisplayName("지원하지 않는 타입이면 예외가 발생한다")
    void 미지원_타입_예외() {
        // given
        FarePolicyStrategyRegistry registry = new FarePolicyStrategyRegistry(
                List.of(new WeekendSurchargeStrategy())
        );

        // when & then
        assertThatThrownBy(() -> registry.resolve(FarePolicyTypeEnum.LONG_STAY_DISCOUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 정책 타입입니다: LONG_STAY_DISCOUNT");
    }
}