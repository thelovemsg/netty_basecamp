package org.example.netty_basecamp.coupon.application;

import org.example.netty_basecamp.domains.common.vo.Inventory;
import org.example.netty_basecamp.domains.common.vo.Money;
import org.example.netty_basecamp.domains.coupon.application.CouponIssueApplicationService;
import org.example.netty_basecamp.domains.coupon.domain.Coupon;
import org.example.netty_basecamp.domains.coupon.domain.IssuedCoupon;
import org.example.netty_basecamp.domains.coupon.domain.service.CouponIssueDomainService;
import org.example.netty_basecamp.domains.fare.domain.Fare;
import org.example.netty_basecamp.domains.fare.domain.FareStatusEnum;
import org.example.netty_basecamp.domains.fare.domain.FareTypeEnum;
import org.example.netty_basecamp.domains.fare.domain.calculation.FareCalculationPipeline;
import org.example.netty_basecamp.domains.fare.domain.calculation.FarePolicyStrategyRegistry;
import org.example.netty_basecamp.domains.fare.domain.calculation.strategies.FixedAmountDiscountStrategy;
import org.example.netty_basecamp.domains.fare.domain.calculation.strategies.LongStayDiscountStrategy;
import org.example.netty_basecamp.domains.fare.domain.calculation.strategies.WeekendSurchargeStrategy;
import org.example.netty_basecamp.domains.fare.domain.policy.CalculationBasisEnum;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicyTypeEnum;
import org.example.netty_basecamp.domains.fare.domain.service.FareCalculationDomainService;
import org.example.netty_basecamp.fake.generator.FakeNumberGenerator;
import org.example.netty_basecamp.fake.generator.FakeTimeGenerator;
import org.example.netty_basecamp.fake.repository.FakeCouponRepository;
import org.example.netty_basecamp.fake.repository.FakeFarePolicyRepository;
import org.example.netty_basecamp.fake.repository.FakeFareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CouponIssueApplicationServiceTest {

    private CouponIssueApplicationService applicationService;
    private FakeFareRepository fareRepository;
    private FakeFarePolicyRepository farePolicyRepository;
    private FakeCouponRepository couponRepository;

    private static final Long FARE_ID = 1L;
    private static final Long COUPON_ID = 1L;
    private static final Long MEMBER_ID = 100L;

    @BeforeEach
    void setUp() {
        fareRepository = new FakeFareRepository();
        farePolicyRepository = new FakeFarePolicyRepository();
        couponRepository = new FakeCouponRepository();

        FarePolicyStrategyRegistry registry = new FarePolicyStrategyRegistry(
                List.of(
                        new WeekendSurchargeStrategy(),
                        new LongStayDiscountStrategy(),
                        new FixedAmountDiscountStrategy()
                )
        );

        FareCalculationPipeline pipeline = new FareCalculationPipeline(registry);
        FareCalculationDomainService fareCalculationDomainService =
                new FareCalculationDomainService(pipeline);

        CouponIssueDomainService couponIssueDomainService =
                new CouponIssueDomainService(
                        new FakeNumberGenerator("COUPON-001"),
                        new FakeTimeGenerator(1000L)
                );

        applicationService = new CouponIssueApplicationService(
                fareRepository,
                farePolicyRepository,
                couponRepository,
                fareCalculationDomainService,
                couponIssueDomainService
        );
    }

    // ========== 헬퍼 메서드 ==========

    private void saveDefaultFare(int basePrice) {
        fareRepository.save(Fare.builder()
                .id(FARE_ID).name("스탠다드 룸").basePrice(Money.of(basePrice))
                .status(FareStatusEnum.ACTIVE).fareType(FareTypeEnum.A_TYPE)
                .createdAt(1000L).modifiedAt(1000L).build());
    }

    private void saveDefaultCoupon() {
        couponRepository.save(Coupon.builder()
                .id(COUPON_ID).description("테스트 쿠폰")
                .inventory(Inventory.createInitial(10))
                .originalPrice(Money.of(100000))
                .expireDate(LocalDate.of(2026, 12, 31))
                .createdAt(1000L).modifiedAt(1000L).build());
    }

    private void savePolicy(Long id, FarePolicyTypeEnum type, String value,
                            CalculationBasisEnum basis, int priority) {
        farePolicyRepository.save(FarePolicy.builder()
                .id(id).fareId(FARE_ID).type(type)
                .value(new BigDecimal(value)).basis(basis)
                .priority(priority).createdAt(1000L).modifiedAt(1000L).build());
    }

    private IssuedCoupon issueDefaultCoupon() {
        return applicationService.issueCoupon(FARE_ID, COUPON_ID, MEMBER_ID).issuedCoupon();
    }

    // ========== 정책 없음 ==========

    @Test
    @DisplayName("정책이 없으면 기본 요금으로 발급된다")
    void 정책_없음_기본_요금() {
        saveDefaultFare(100000);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        assertThat(issued.getAppliedPrice()).isEqualTo(Money.of(100000));
    }

    // ========== 단일 정책 ==========

    @Test
    @DisplayName("주말 할증 20%가 적용된다")
    void 단일_정책_주말_할증() {
        saveDefaultFare(100000);
        savePolicy(1L, FarePolicyTypeEnum.WEEKEND_SURCHARGE, "20",
                CalculationBasisEnum.ORIGINAL, 1);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        // 10만 + 2만(20%) = 12만
        assertThat(issued.getAppliedPrice()).isEqualTo(Money.of(120000));
    }

    @Test
    @DisplayName("장기 투숙 할인 10%가 적용된다")
    void 단일_정책_장기_할인() {
        saveDefaultFare(100000);
        savePolicy(1L, FarePolicyTypeEnum.LONG_STAY_DISCOUNT, "10",
                CalculationBasisEnum.ORIGINAL, 1);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        // 10만 - 1만(10%) = 9만
        assertThat(issued.getAppliedPrice()).isEqualTo(Money.of(90000));
    }

    @Test
    @DisplayName("고정 금액 5000원 할인이 적용된다")
    void 단일_정책_고정_금액_할인() {
        saveDefaultFare(100000);
        savePolicy(1L, FarePolicyTypeEnum.FIXED_AMOUNT_DISCOUNT, "5000",
                CalculationBasisEnum.ORIGINAL, 1);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        // 10만 - 5000 = 9만5천
        assertThat(issued.getAppliedPrice()).isEqualTo(Money.of(95000));
    }

    // ========== 다중 정책 (단리) ==========

    @Test
    @DisplayName("단리: 주말할증 20% + 장기할인 10% = 11만")
    void 다중_정책_단리_할증_할인() {
        saveDefaultFare(100000);
        savePolicy(1L, FarePolicyTypeEnum.WEEKEND_SURCHARGE, "20",
                CalculationBasisEnum.ORIGINAL, 1);
        savePolicy(2L, FarePolicyTypeEnum.LONG_STAY_DISCOUNT, "10",
                CalculationBasisEnum.ORIGINAL, 2);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        // 단리: 10만 + 2만(10만의 20%) - 1만(10만의 10%) = 11만
        assertThat(issued.getAppliedPrice()).isEqualTo(Money.of(110000));
    }

    @Test
    @DisplayName("단리: 주말할증 20% + 고정할인 5000원 = 11만5천")
    void 다중_정책_단리_할증_고정할인() {
        saveDefaultFare(100000);
        savePolicy(1L, FarePolicyTypeEnum.WEEKEND_SURCHARGE, "20",
                CalculationBasisEnum.ORIGINAL, 1);
        savePolicy(2L, FarePolicyTypeEnum.FIXED_AMOUNT_DISCOUNT, "5000",
                CalculationBasisEnum.ORIGINAL, 2);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        // 단리: 10만 + 2만(20%) - 5000 = 11만5천
        assertThat(issued.getAppliedPrice()).isEqualTo(Money.of(115000));
    }

    // ========== 다중 정책 (복리) ==========

    @Test
    @DisplayName("복리: 주말할증 20% 후 장기할인 10% = 10만8천")
    void 다중_정책_복리_할증_후_할인() {
        saveDefaultFare(100000);
        savePolicy(1L, FarePolicyTypeEnum.WEEKEND_SURCHARGE, "20",
                CalculationBasisEnum.ORIGINAL, 1);
        savePolicy(2L, FarePolicyTypeEnum.LONG_STAY_DISCOUNT, "10",
                CalculationBasisEnum.ACCUMULATED, 2);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        // 10만 + 2만(20%) = 12만 → 12만 - 1만2천(12만의 10%) = 10만8천
        assertThat(issued.getAppliedPrice()).isEqualTo(Money.of(108000));
    }

    @Test
    @DisplayName("복리: 장기할인 10% 후 주말할증 20% = 10만8천")
    void 다중_정책_복리_할인_후_할증() {
        saveDefaultFare(100000);
        savePolicy(1L, FarePolicyTypeEnum.LONG_STAY_DISCOUNT, "10",
                CalculationBasisEnum.ORIGINAL, 1);
        savePolicy(2L, FarePolicyTypeEnum.WEEKEND_SURCHARGE, "20",
                CalculationBasisEnum.ACCUMULATED, 2);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        // 10만 - 1만(10%) = 9만 → 9만 + 1만8천(9만의 20%) = 10만8천
        assertThat(issued.getAppliedPrice()).isEqualTo(Money.of(108000));
    }

    // ========== 세 개 이상 정책 ==========

    @Test
    @DisplayName("3개 정책 혼합: 주말할증 20%(단리) + 장기할인 10%(복리) + 고정할인 3000원")
    void 세개_정책_혼합() {
        saveDefaultFare(100000);
        savePolicy(1L, FarePolicyTypeEnum.WEEKEND_SURCHARGE, "20",
                CalculationBasisEnum.ORIGINAL, 1);
        savePolicy(2L, FarePolicyTypeEnum.LONG_STAY_DISCOUNT, "10",
                CalculationBasisEnum.ACCUMULATED, 2);
        savePolicy(3L, FarePolicyTypeEnum.FIXED_AMOUNT_DISCOUNT, "3000",
                CalculationBasisEnum.ORIGINAL, 3);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        // 10만 + 2만(10만의 20%) = 12만
        // 12만 - 1만2천(12만의 10%) = 10만8천
        // 10만8천 - 3000 = 10만5천
        assertThat(issued.getAppliedPrice()).isEqualTo(Money.of(105000));
    }

    // ========== priority 순서 검증 ==========

    @Test
    @DisplayName("priority 역순으로 입력해도 순서대로 적용된다")
    void priority_역순_입력() {
        saveDefaultFare(100000);
        savePolicy(2L, FarePolicyTypeEnum.LONG_STAY_DISCOUNT, "10",
                CalculationBasisEnum.ACCUMULATED, 2);
        savePolicy(1L, FarePolicyTypeEnum.WEEKEND_SURCHARGE, "20",
                CalculationBasisEnum.ORIGINAL, 1);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        // priority 1(할증) 먼저, priority 2(할인) 나중
        // 10만 + 2만 = 12만 → 12만 - 1만2천 = 10만8천
        assertThat(issued.getAppliedPrice()).isEqualTo(Money.of(108000));
    }

    // ========== 발급 정보 검증 ==========

    @Test
    @DisplayName("발급된 쿠폰에 회원 정보와 쿠폰 번호가 정확하다")
    void 발급_정보_검증() {
        saveDefaultFare(100000);
        saveDefaultCoupon();

        IssuedCoupon issued = issueDefaultCoupon();

        assertThat(issued.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(issued.getCouponNumber()).isEqualTo("COUPON-001");
        assertThat(issued.getCouponId()).isEqualTo(COUPON_ID);
    }
}