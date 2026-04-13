package org.example.netty_basecamp.fare.application;

import org.example.netty_basecamp.domains.common.vo.Money;
import org.example.netty_basecamp.domains.fare.application.FareApplicationService;
import org.example.netty_basecamp.domains.fare.domain.Fare;
import org.example.netty_basecamp.domains.fare.domain.FareCreate;
import org.example.netty_basecamp.domains.fare.domain.FareStatusEnum;
import org.example.netty_basecamp.fake.generator.FakeTimeGenerator;
import org.example.netty_basecamp.fake.repository.FakeFareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FareApplicationServiceTest {

    private FareApplicationService fareService;
    private FakeFareRepository fareRepository;

    private static final long FIXED_TIME = 1000L;

    @BeforeEach
    void setUp() {
        fareRepository = new FakeFareRepository();
        fareService = new FareApplicationService(fareRepository, new FakeTimeGenerator(FIXED_TIME));
    }

    // ========== findById ==========

    @Test
    @DisplayName("저장된 Fare를 id로 조회할 수 있다")
    void findById_성공() {
        Fare saved = fareService.create(new FareCreate("스탠다드 룸", Money.of(100000)));

        Fare found = fareService.findById(saved.getId());

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo("스탠다드 룸");
    }

    @Test
    @DisplayName("존재하지 않는 id 조회 시 예외가 발생한다")
    void findById_없는_id_예외() {
        assertThatThrownBy(() -> fareService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    // ========== create ==========

    @Test
    @DisplayName("Fare 생성 시 id가 자동으로 부여된다")
    void create_id_자동_부여() {
        Fare fare = fareService.create(new FareCreate("스탠다드 룸", Money.of(100000)));

        assertThat(fare.getId()).isNotNull();
    }

    @Test
    @DisplayName("Fare 생성 시 name과 basePrice가 저장된다")
    void create_필드_저장() {
        Fare fare = fareService.create(new FareCreate("디럭스 룸", Money.of(200000)));

        assertThat(fare.getName()).isEqualTo("디럭스 룸");
        assertThat(fare.getBasePrice()).isEqualTo(Money.of(200000));
    }

    @Test
    @DisplayName("Fare 생성 시 상태는 ACTIVE다")
    void create_초기_상태_ACTIVE() {
        Fare fare = fareService.create(new FareCreate("스탠다드 룸", Money.of(100000)));

        assertThat(fare.getStatus()).isEqualTo(FareStatusEnum.ACTIVE);
    }

    @Test
    @DisplayName("Fare 생성 시 createdAt, modifiedAt에 현재 시각이 기록된다")
    void create_시간_기록() {
        Fare fare = fareService.create(new FareCreate("스탠다드 룸", Money.of(100000)));

        assertThat(fare.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(fare.getModifiedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    @DisplayName("여러 Fare를 생성하면 각각 다른 id가 부여된다")
    void create_다중_생성_id_구분() {
        Fare fare1 = fareService.create(new FareCreate("스탠다드 룸", Money.of(100000)));
        Fare fare2 = fareService.create(new FareCreate("디럭스 룸", Money.of(200000)));

        assertThat(fare1.getId()).isNotEqualTo(fare2.getId());
    }

    // ========== delete ==========

    @Test
    @DisplayName("Fare 삭제 후 조회하면 예외가 발생한다")
    void delete_삭제_후_조회_예외() {
        Fare fare = fareService.create(new FareCreate("스탠다드 룸", Money.of(100000)));

        fareService.delete(fare.getId());

        assertThatThrownBy(() -> fareService.findById(fare.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("존재하지 않는 Fare 삭제 시 예외가 발생한다")
    void delete_없는_id_예외() {
        assertThatThrownBy(() -> fareService.delete(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }
}
