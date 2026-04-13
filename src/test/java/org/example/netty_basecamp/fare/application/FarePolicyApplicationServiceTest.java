package org.example.netty_basecamp.fare.application;

import org.example.netty_basecamp.domains.common.vo.Money;
import org.example.netty_basecamp.domains.fare.application.FarePolicyApplicationService;
import org.example.netty_basecamp.domains.fare.domain.Fare;
import org.example.netty_basecamp.domains.fare.domain.FareStatusEnum;
import org.example.netty_basecamp.domains.fare.domain.policy.CalculationBasisEnum;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicyCreate;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicyTypeEnum;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicyUpdate;
import org.example.netty_basecamp.fake.generator.FakeTimeGenerator;
import org.example.netty_basecamp.fake.repository.FakeFarePolicyRepository;
import org.example.netty_basecamp.fake.repository.FakeFareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FarePolicyApplicationServiceTest {

    private FarePolicyApplicationService policyService;
    private FakeFareRepository fareRepository;
    private FakeFarePolicyRepository farePolicyRepository;

    private static final long FIXED_TIME = 1000L;
    private static final Long FARE_ID = 1L;
    private static final Long OTHER_FARE_ID = 2L;

    @BeforeEach
    void setUp() {
        fareRepository = new FakeFareRepository();
        farePolicyRepository = new FakeFarePolicyRepository();
        policyService = new FarePolicyApplicationService(fareRepository, farePolicyRepository, new FakeTimeGenerator(FIXED_TIME));

        // 기본 Fare 두 개 등록
        fareRepository.save(Fare.builder().id(FARE_ID).name("스탠다드 룸")
                .basePrice(Money.of(100000)).status(FareStatusEnum.ACTIVE)
                .createdAt(FIXED_TIME).modifiedAt(FIXED_TIME).build());
        fareRepository.save(Fare.builder().id(OTHER_FARE_ID).name("디럭스 룸")
                .basePrice(Money.of(200000)).status(FareStatusEnum.ACTIVE)
                .createdAt(FIXED_TIME).modifiedAt(FIXED_TIME).build());
    }

    // ========== 헬퍼 ==========

    private FarePolicy addDefaultPolicy(Long fareId) {
        return policyService.add(fareId, new FarePolicyCreate(
                FarePolicyTypeEnum.FIXED_AMOUNT_DISCOUNT,
                new BigDecimal("5000"),
                CalculationBasisEnum.ORIGINAL,
                1
        ));
    }

    // ========== findByFareId ==========

    @Test
    @DisplayName("해당 fareId에 속한 정책만 반환한다")
    void findByFareId_해당_정책만_반환() {
        addDefaultPolicy(FARE_ID);
        addDefaultPolicy(FARE_ID);
        addDefaultPolicy(OTHER_FARE_ID);

        List<FarePolicy> policies = policyService.findByFareId(FARE_ID);

        assertThat(policies).hasSize(2);
        assertThat(policies).allMatch(p -> p.getFareId().equals(FARE_ID));
    }

    @Test
    @DisplayName("정책이 없으면 빈 리스트를 반환한다")
    void findByFareId_정책_없음_빈_리스트() {
        List<FarePolicy> policies = policyService.findByFareId(FARE_ID);

        assertThat(policies).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 fareId 조회 시 예외가 발생한다")
    void findByFareId_없는_fareId_예외() {
        assertThatThrownBy(() -> policyService.findByFareId(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    // ========== findById ==========

    @Test
    @DisplayName("policyId로 단건 조회할 수 있다")
    void findById_성공() {
        FarePolicy saved = addDefaultPolicy(FARE_ID);

        FarePolicy found = policyService.findById(FARE_ID, saved.getId());

        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("존재하지 않는 policyId 조회 시 예외가 발생한다")
    void findById_없는_policyId_예외() {
        assertThatThrownBy(() -> policyService.findById(FARE_ID, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("다른 fareId의 정책을 조회하면 예외가 발생한다")
    void findById_다른_fareId_예외() {
        FarePolicy saved = addDefaultPolicy(OTHER_FARE_ID);

        assertThatThrownBy(() -> policyService.findById(FARE_ID, saved.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========== add ==========

    @Test
    @DisplayName("정책 추가 시 id가 자동으로 부여된다")
    void add_id_자동_부여() {
        FarePolicy policy = addDefaultPolicy(FARE_ID);

        assertThat(policy.getId()).isNotNull();
    }

    @Test
    @DisplayName("정책 추가 시 fareId, type, value, basis, priority가 저장된다")
    void add_필드_저장() {
        FarePolicy policy = policyService.add(FARE_ID, new FarePolicyCreate(
                FarePolicyTypeEnum.WEEKEND_SURCHARGE,
                new BigDecimal("20"),
                CalculationBasisEnum.ACCUMULATED,
                2
        ));

        assertThat(policy.getFareId()).isEqualTo(FARE_ID);
        assertThat(policy.getType()).isEqualTo(FarePolicyTypeEnum.WEEKEND_SURCHARGE);
        assertThat(policy.getValue()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(policy.getBasis()).isEqualTo(CalculationBasisEnum.ACCUMULATED);
        assertThat(policy.getPriority()).isEqualTo(2);
    }

    @Test
    @DisplayName("여러 정책을 추가하면 각각 다른 id가 부여된다")
    void add_다중_추가_id_구분() {
        FarePolicy policy1 = addDefaultPolicy(FARE_ID);
        FarePolicy policy2 = addDefaultPolicy(FARE_ID);

        assertThat(policy1.getId()).isNotEqualTo(policy2.getId());
    }

    @Test
    @DisplayName("존재하지 않는 fareId에 정책 추가 시 예외가 발생한다")
    void add_없는_fareId_예외() {
        assertThatThrownBy(() -> addDefaultPolicy(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    // ========== update ==========

    @Test
    @DisplayName("정책의 value, basis, priority를 수정할 수 있다")
    void update_필드_수정() {
        FarePolicy saved = addDefaultPolicy(FARE_ID);

        FarePolicy updated = policyService.update(FARE_ID, saved.getId(),
                new FarePolicyUpdate(new BigDecimal("3000"), CalculationBasisEnum.ACCUMULATED, 5));

        assertThat(updated.getValue()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(updated.getBasis()).isEqualTo(CalculationBasisEnum.ACCUMULATED);
        assertThat(updated.getPriority()).isEqualTo(5);
    }

    @Test
    @DisplayName("수정 후 modifiedAt이 갱신된다")
    void update_modifiedAt_갱신() {
        FarePolicy saved = addDefaultPolicy(FARE_ID);

        FarePolicy updated = policyService.update(FARE_ID, saved.getId(),
                new FarePolicyUpdate(new BigDecimal("3000"), CalculationBasisEnum.ORIGINAL, 1));

        assertThat(updated.getModifiedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    @DisplayName("다른 fareId의 정책 수정 시 예외가 발생한다")
    void update_다른_fareId_예외() {
        FarePolicy saved = addDefaultPolicy(OTHER_FARE_ID);

        assertThatThrownBy(() -> policyService.update(FARE_ID, saved.getId(),
                new FarePolicyUpdate(new BigDecimal("3000"), CalculationBasisEnum.ORIGINAL, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========== delete ==========

    @Test
    @DisplayName("정책 삭제 후 조회하면 예외가 발생한다")
    void delete_삭제_후_조회_예외() {
        FarePolicy saved = addDefaultPolicy(FARE_ID);

        policyService.delete(FARE_ID, saved.getId());

        assertThatThrownBy(() -> policyService.findById(FARE_ID, saved.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("삭제 후 같은 fareId의 다른 정책은 남아있다")
    void delete_다른_정책_유지() {
        FarePolicy target = addDefaultPolicy(FARE_ID);
        FarePolicy other = addDefaultPolicy(FARE_ID);

        policyService.delete(FARE_ID, target.getId());

        List<FarePolicy> remaining = policyService.findByFareId(FARE_ID);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(other.getId());
    }

    @Test
    @DisplayName("다른 fareId의 정책 삭제 시 예외가 발생한다")
    void delete_다른_fareId_예외() {
        FarePolicy saved = addDefaultPolicy(OTHER_FARE_ID);

        assertThatThrownBy(() -> policyService.delete(FARE_ID, saved.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
