package org.example.netty_basecamp.domain;

import org.example.netty_basecamp.domain.common.service.TimeGenerator;
import org.example.netty_basecamp.domain.common.vo.Inventory;
import org.example.netty_basecamp.domain.common.vo.Money;
import org.example.netty_basecamp.domain.coupon.domain.Coupon;
import org.example.netty_basecamp.domain.coupon.domain.CouponCreate;
import org.example.netty_basecamp.domain.coupon.domain.CouponUpdate;
import org.example.netty_basecamp.fake.generator.FakeTimeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CouponTest {

    private TimeGenerator createTimeGenerator;
    private TimeGenerator modifyTimeGenerator;

    @BeforeEach
    public void setup() {
        createTimeGenerator = new FakeTimeGenerator(1000L);
        modifyTimeGenerator = new FakeTimeGenerator(9999L);
    }

    @Test
    @DisplayName("쿠폰 생성 시 정보가 올바르게 저장된다")
    void 쿠폰_생성() {
        // given
        CouponCreate couponCreate = CouponCreate.builder()
                .description("테스트 생성입니다.")
                .totalCount(1000)
                .originalPrice(Money.of(10000))
                .expireDate(LocalDate.of(2027, 12, 31))
                .build();


        // when
        Coupon coupon = Coupon.from(couponCreate, createTimeGenerator.millis());

        // then
        assertThat(coupon.getDescription()).isEqualTo("테스트 생성입니다.");
        assertThat(coupon.getOriginalPrice()).isEqualTo(Money.of(10000));
        assertThat(coupon.getInventory().getTotalCount()).isEqualTo(1000);
        assertThat(coupon.getInventory().getUsedCount()).isEqualTo(0);
        assertThat(coupon.getCreatedAt()).isEqualTo(1000L);
        assertThat(coupon.getModifiedAt()).isEqualTo(1000L);
        assertThat(coupon.getId()).isNull();
    }

    @Test
    @DisplayName("쿠폰 일반 정보 수정 시 설명과 만료일이 변경된다")
    void 쿠폰_정보_수정() {
        // given
        Coupon coupon = Coupon.builder()
                .id(1L)
                .description("기존 설명")
                .inventory(Inventory.builder().totalCount(1000).usedCount(0).build())
                .originalPrice(Money.of(10000))
                .expireDate(LocalDate.of(2026, 12, 31))
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        CouponUpdate couponUpdate = CouponUpdate.builder()
                .description("변경된 설명")
                .expireDate(LocalDate.of(2027, 12, 31))
                .build();

        // when
        Coupon updatedCoupon = coupon.updateCouponInfo(couponUpdate, modifyTimeGenerator.millis());

        // then
        assertThat(updatedCoupon.getDescription()).isEqualTo("변경된 설명");
        assertThat(updatedCoupon.getExpireDate()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(updatedCoupon.getInventory()).isEqualTo(coupon.getInventory());
        assertThat(updatedCoupon.getId()).isEqualTo(1L);
        assertThat(updatedCoupon.getCreatedAt()).isEqualTo(1000L);
        assertThat(updatedCoupon.getModifiedAt()).isEqualTo(9999L);
    }

    @Test
    @DisplayName("발급 이력이 없으면 재고 정보를 수정할 수 있다")
    void 쿠폰_재고_수정() {
        // given
        Coupon coupon = Coupon.builder()
                .id(1L)
                .description("발급 전 쿠폰")
                .inventory(Inventory.builder().totalCount(1000).usedCount(0).build())
                .originalPrice(Money.of(10000))
                .expireDate(LocalDate.of(2027, 12, 31))
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        CouponUpdate couponUpdate = CouponUpdate.builder()
                .totalCount(2000)
                .usedCount(0)
                .build();

        // when
        Coupon updatedCoupon = coupon.updateInventoryInfo(couponUpdate, modifyTimeGenerator.millis());

        // then
        assertThat(updatedCoupon.getInventory().getTotalCount()).isEqualTo(2000);
        assertThat(updatedCoupon.getInventory().getUsedCount()).isEqualTo(0);
        assertThat(updatedCoupon.getDescription()).isEqualTo("발급 전 쿠폰");
        assertThat(updatedCoupon.getCreatedAt()).isEqualTo(1000L);
        assertThat(updatedCoupon.getModifiedAt()).isEqualTo(9999L);
    }

    @Test
    @DisplayName("예약 시 재고가 1 감소하고 modifiedAt이 갱신된다")
    void 쿠폰_예약() {
        // given
        Coupon coupon = Coupon.builder()
                .id(1L)
                .description("예약 테스트")
                .inventory(Inventory.builder().totalCount(1000).usedCount(0).build())
                .originalPrice(Money.of(10000))
                .expireDate(LocalDate.of(2027, 12, 31))
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when
        Coupon reservedCoupon = coupon.reserve(modifyTimeGenerator.millis());

        // then
        assertThat(reservedCoupon.getInventory().getUsedCount()).isEqualTo(1);
        assertThat(reservedCoupon.getInventory().getTotalCount()).isEqualTo(1000);
        assertThat(reservedCoupon.getInventory().getUsedCount()).isEqualTo(1);
        assertThat(reservedCoupon.getInventory().getRemainCount()).isEqualTo(1000-1);
        assertThat(reservedCoupon.getOriginalPrice()).isEqualTo(Money.of(10000));
        assertThat(reservedCoupon.getCreatedAt()).isEqualTo(1000L);
        assertThat(reservedCoupon.getModifiedAt()).isEqualTo(9999L);
    }

    @Test
    @DisplayName("만료된 쿠폰은 예약할 수 없다")
    void 쿠폰_예약_만료_예외() {
        // given
        Coupon coupon = Coupon.builder()
                .id(1L)
                .description("만료 쿠폰")
                .inventory(Inventory.builder().totalCount(1000).usedCount(0).build())
                .originalPrice(Money.of(10000))
                .expireDate(LocalDate.of(2020, 1, 1))  // 과거 날짜
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when & then
        assertThatThrownBy(() -> coupon.reserve(modifyTimeGenerator.millis()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("만료된 쿠폰입니다.");
    }

    @Test
    @DisplayName("예약은 기존 객체를 변경하지 않고 새 객체를 반환한다")
    void 쿠폰_예약_새객체_반환() {
        // given
        Coupon coupon = Coupon.builder()
                .id(1L)
                .description("예약 테스트")
                .inventory(Inventory.builder().totalCount(1000).usedCount(0).build())
                .originalPrice(Money.of(10000))
                .expireDate(LocalDate.of(2027, 12, 31))
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        // when
        Coupon reservedCoupon = coupon.reserve(modifyTimeGenerator.millis());

        // then
        assertThat(reservedCoupon).isNotSameAs(coupon);
        assertThat(coupon.getInventory().getUsedCount()).isEqualTo(0);
    }
}