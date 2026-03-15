package org.example.netty_basecamp.coupon.domain;

import org.example.netty_basecamp.domains.common.service.NumberGenerator;
import org.example.netty_basecamp.domains.common.service.TimeGenerator;
import org.example.netty_basecamp.domains.common.vo.Money;
import org.example.netty_basecamp.domains.coupon.domain.Coupon;
import org.example.netty_basecamp.domains.coupon.domain.IssuedCoupon;
import org.example.netty_basecamp.domains.coupon.domain.vo.IssuedCouponStatusEnum;
import org.example.netty_basecamp.fake.generator.FakeNumberGenerator;
import org.example.netty_basecamp.fake.generator.FakeTimeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssuedCouponTest {

    private TimeGenerator createTimeGenerator;
    private TimeGenerator modifyTimeGenerator;
    private NumberGenerator numberGenerator;

    @BeforeEach
    public void setup() {
        createTimeGenerator = new FakeTimeGenerator(1000L);
        modifyTimeGenerator = new FakeTimeGenerator(9999L);
        numberGenerator = new FakeNumberGenerator("20250226-000001-ABC123");
    }

    @Test
    @DisplayName("쿠폰 발급 시 UNUSED 상태로 생성되고 정가가 적용된다")
    void 쿠폰_발급() {
        // given
        Coupon coupon = Coupon.builder()
                .id(1L)
                .description("10% 할인 쿠폰")
                .originalPrice(Money.of(10000))
                .build();


        // when
        long currentMillis = createTimeGenerator.millis();
        String generatedNumber = numberGenerator.generate(coupon);

        IssuedCoupon issuedCoupon = IssuedCoupon.issue(coupon, 1L, generatedNumber, currentMillis, Money.of(10000));

        // then
        assertThat(issuedCoupon.getCouponId()).isEqualTo(1L);
        assertThat(issuedCoupon.getMemberId()).isEqualTo(1L);
        assertThat(issuedCoupon.getCouponNumber()).isEqualTo("20250226-000001-ABC123");
        assertThat(issuedCoupon.getAppliedPrice()).isEqualTo(Money.of(10000));
        assertThat(issuedCoupon.getStatus()).isEqualTo(IssuedCouponStatusEnum.UNUSED);
        assertThat(issuedCoupon.getIssuedAt()).isEqualTo(1000L);
        assertThat(issuedCoupon.getId()).isNull();
    }

    @Test
    @DisplayName("UNUSED 쿠폰을 사용하면 USED 상태로 변경되고 가격과 시간이 유지된다")
    void 쿠폰_사용() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.builder()
                .id(1L)
                .couponId(1L)
                .memberId(1L)
                .couponNumber("20250226-000001-ABC123")
                .appliedPrice(Money.of(10000))
                .status(IssuedCouponStatusEnum.UNUSED)
                .issuedAt(1000L)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when
        long usingTime = modifyTimeGenerator.millis();
        IssuedCoupon usedCoupon = issuedCoupon.use(usingTime);

        // then
        assertThat(usedCoupon.getStatus()).isEqualTo(IssuedCouponStatusEnum.USED);
        assertThat(usedCoupon.getCouponNumber()).isEqualTo("20250226-000001-ABC123");
        assertThat(usedCoupon.getAppliedPrice()).isEqualTo(Money.of(10000));
        assertThat(usedCoupon.getCreatedAt()).isEqualTo(1000L);
        assertThat(usedCoupon.getModifiedAt()).isEqualTo(9999L);
    }

    @Test
    @DisplayName("UNUSED가 아닌 쿠폰을 사용하면 예외가 발생한다")
    void 쿠폰_사용_이미사용된_쿠폰_예외() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.builder()
                .id(1L)
                .couponId(1L)
                .memberId(1L)
                .couponNumber("20250226-000001-ABC123")
                .appliedPrice(Money.of(10000))
                .status(IssuedCouponStatusEnum.USED)
                .issuedAt(1000L)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when & then
        long usingTime = modifyTimeGenerator.millis();
        assertThatThrownBy(() -> issuedCoupon.use(usingTime))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("사용 가능한 쿠폰이 아닙니다.");
    }

    @Test
    @DisplayName("만료된 쿠폰은 사용할 수 없다")
    void 쿠폰_사용_만료된_쿠폰_예외() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.builder()
                .id(1L)
                .couponId(1L)
                .memberId(1L)
                .couponNumber("20250226-000001-ABC123")
                .appliedPrice(Money.of(10000))
                .status(IssuedCouponStatusEnum.EXPIRED)
                .issuedAt(1000L)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when & then
        long usingTime = modifyTimeGenerator.millis();
        assertThatThrownBy(() -> issuedCoupon.use(usingTime))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("사용 가능한 쿠폰이 아닙니다.");
    }

    @Test
    @DisplayName("쿠폰을 만료하면 EXPIRED 상태로 변경되고 가격과 시간이 유지된다")
    void 쿠폰_만료() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.builder()
                .id(1L)
                .couponId(1L)
                .memberId(1L)
                .couponNumber("20250226-000001-ABC123")
                .appliedPrice(Money.of(10000))
                .status(IssuedCouponStatusEnum.UNUSED)
                .issuedAt(1000L)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when
        long usingTime = modifyTimeGenerator.millis();
        IssuedCoupon expiredCoupon = issuedCoupon.expire(usingTime);

        // then
        assertThat(expiredCoupon.getStatus()).isEqualTo(IssuedCouponStatusEnum.EXPIRED);
        assertThat(expiredCoupon.getCouponNumber()).isEqualTo("20250226-000001-ABC123");
        assertThat(expiredCoupon.getAppliedPrice()).isEqualTo(Money.of(10000));
        assertThat(expiredCoupon.getCreatedAt()).isEqualTo(1000L);
        assertThat(expiredCoupon.getModifiedAt()).isEqualTo(9999L);
    }

    @Test
    @DisplayName("이미 사용된 쿠폰은 만료할 수 없다")
    void 쿠폰_만료_이미사용된_쿠폰_예외() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.builder()
                .id(1L)
                .couponId(1L)
                .memberId(1L)
                .couponNumber("20250226-000001-ABC123")
                .appliedPrice(Money.of(10000))
                .status(IssuedCouponStatusEnum.USED)
                .issuedAt(1000L)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when & then
        long usingTime = modifyTimeGenerator.millis();
        assertThatThrownBy(() -> issuedCoupon.expire(usingTime))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 사용된 쿠폰은 만료할 수 없습니다.");
    }

    @Test
    @DisplayName("use()는 기존 객체를 변경하지 않고 새 객체를 반환한다")
    void 쿠폰_사용_새객체_반환() {
        // given
        IssuedCoupon issuedCoupon = IssuedCoupon.builder()
                .id(1L)
                .couponId(1L)
                .memberId(1L)
                .couponNumber("20250226-000001-ABC123")
                .appliedPrice(Money.of(10000))
                .status(IssuedCouponStatusEnum.UNUSED)
                .issuedAt(1000L)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when
        long usingTime = modifyTimeGenerator.millis();
        IssuedCoupon usedCoupon = issuedCoupon.use(usingTime);

        // then
        assertThat(usedCoupon).isNotSameAs(issuedCoupon);
        assertThat(issuedCoupon.getStatus()).isEqualTo(IssuedCouponStatusEnum.UNUSED);
        assertThat(issuedCoupon.getModifiedAt()).isEqualTo(1000L);
    }
}