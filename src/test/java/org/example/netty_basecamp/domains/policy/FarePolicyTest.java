package org.example.netty_basecamp.domains.policy;

import org.example.netty_basecamp.domains.common.service.TimeGenerator;
import org.example.netty_basecamp.domains.fare.domain.policy.*;
import org.example.netty_basecamp.fake.generator.FakeTimeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FarePolicyTest {

    private TimeGenerator createTimeGenerator;
    private TimeGenerator modifyTimeGenerator;

    @BeforeEach
    void setUp() {
        createTimeGenerator = new FakeTimeGenerator(1000L);
        modifyTimeGenerator = new FakeTimeGenerator(2000L);
    }

    @Test
    @DisplayName("FarePolicyCreate로 정책을 생성할 수 있다")
    void FarePolicy_생성() {
        // given
        FarePolicyCreate create = FarePolicyCreate.builder()
                .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                .value(new BigDecimal("20"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .build();

        // when
        FarePolicy policy = FarePolicy.from(create, 1L, createTimeGenerator.millis());

        // then
        assertThat(policy.getFareId()).isEqualTo(1L);
        assertThat(policy.getType()).isEqualTo(FarePolicyTypeEnum.WEEKEND_SURCHARGE);
        assertThat(policy.getValue()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(policy.getBasis()).isEqualTo(CalculationBasisEnum.ORIGINAL);
        assertThat(policy.getPriority()).isEqualTo(1);
        assertThat(policy.getCreatedAt()).isEqualTo(createTimeGenerator.millis());
        assertThat(policy.getModifiedAt()).isEqualTo(createTimeGenerator.millis());
    }

    @Test
    @DisplayName("정책 값이 null이면 예외가 발생한다")
    void FarePolicy_생성_value_null_예외() {
        assertThatThrownBy(
            () -> FarePolicy.builder()
                .fareId(1L)
                .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                .value(null)
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(createTimeGenerator.millis())
                .modifiedAt(createTimeGenerator.millis())
                .build()
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정책 값은 필수입니다.");
    }

    @Test
    @DisplayName("정책 값이 음수이면 예외가 발생한다")
    void FarePolicy_생성_value_음수_예외() {
        assertThatThrownBy(
            () -> FarePolicy.builder()
                .fareId(1L)
                .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                .value(new BigDecimal("-1"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(createTimeGenerator.millis())
                .modifiedAt(createTimeGenerator.millis())
                .build()
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("정책 값은 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("퍼센트 타입에서 값이 100을 초과하면 예외가 발생한다")
    void FarePolicy_생성_퍼센트_100초과_예외() {
        assertThatThrownBy(
            () -> FarePolicy.builder()
                .fareId(1L)
                .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                .value(new BigDecimal("101"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(createTimeGenerator.millis())
                .modifiedAt(createTimeGenerator.millis())
                .build()
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("퍼센트 값은 100을 초과할 수 없습니다.");
    }

    @Test
    @DisplayName("퍼센트 타입에서 값이 100이면 정상 생성된다")
    void FarePolicy_생성_퍼센트_100_경계값() {
        FarePolicy policy = FarePolicy.builder()
                .fareId(1L)
                .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                .value(new BigDecimal("100"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(createTimeGenerator.millis())
                .modifiedAt(createTimeGenerator.millis())
                .build();

        assertThat(policy.getValue()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("FarePolicyUpdate로 정책을 수정할 수 있다")
    void FarePolicy_수정() {
        // given
        FarePolicy policy = FarePolicy.builder()
                .id(1L)
                .fareId(1L)
                .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                .value(new BigDecimal("20"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(createTimeGenerator.millis())
                .modifiedAt(createTimeGenerator.millis())
                .build();

        FarePolicyUpdate update = FarePolicyUpdate.builder()
                .value(new BigDecimal("30"))
                .basis(CalculationBasisEnum.ACCUMULATED)
                .priority(2)
                .build();

        // when
        FarePolicy updated = policy.update(update, modifyTimeGenerator.millis());

        // then
        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getFareId()).isEqualTo(1L);
        assertThat(updated.getType()).isEqualTo(FarePolicyTypeEnum.WEEKEND_SURCHARGE);
        assertThat(updated.getValue()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(updated.getBasis()).isEqualTo(CalculationBasisEnum.ACCUMULATED);
        assertThat(updated.getPriority()).isEqualTo(2);
        assertThat(updated.getCreatedAt()).isEqualTo(createTimeGenerator.millis());
        assertThat(updated.getModifiedAt()).isEqualTo(modifyTimeGenerator.millis());
    }

    @Test
    @DisplayName("update()는 기존 객체를 변경하지 않고 새 객체를 반환한다")
    void FarePolicy_수정_불변성() {
        // given
        FarePolicy policy = FarePolicy.builder()
                .id(1L)
                .fareId(1L)
                .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                .value(new BigDecimal("20"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(createTimeGenerator.millis())
                .modifiedAt(createTimeGenerator.millis())
                .build();

        FarePolicyUpdate update = FarePolicyUpdate.builder()
                .value(new BigDecimal("30"))
                .basis(CalculationBasisEnum.ACCUMULATED)
                .priority(2)
                .build();

        // when
        FarePolicy updated = policy.update(update, modifyTimeGenerator.millis());

        // then
        assertThat(updated).isNotSameAs(policy);
        assertThat(policy.getValue()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(policy.getBasis()).isEqualTo(CalculationBasisEnum.ORIGINAL);
        assertThat(policy.getPriority()).isEqualTo(1);
    }

    @Test
    @DisplayName("update()시 type은 변경되지 않는다")
    void FarePolicy_수정_type_불변() {
        // given
        FarePolicy policy = FarePolicy.builder()
                .id(1L)
                .fareId(1L)
                .type(FarePolicyTypeEnum.WEEKEND_SURCHARGE)
                .value(new BigDecimal("20"))
                .basis(CalculationBasisEnum.ORIGINAL)
                .priority(1)
                .createdAt(createTimeGenerator.millis())
                .modifiedAt(createTimeGenerator.millis())
                .build();

        FarePolicyUpdate update = FarePolicyUpdate.builder()
                .value(new BigDecimal("30"))
                .basis(CalculationBasisEnum.ACCUMULATED)
                .priority(2)
                .build();

        // when
        FarePolicy updated = policy.update(update, modifyTimeGenerator.millis());

        // then
        assertThat(updated.getType()).isEqualTo(FarePolicyTypeEnum.WEEKEND_SURCHARGE);
    }
}