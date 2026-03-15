package org.example.netty_basecamp.coupon.domain.service;

import org.example.netty_basecamp.domains.common.service.NumberGenerator;
import org.example.netty_basecamp.domains.common.service.TimeGenerator;
import org.example.netty_basecamp.domains.common.vo.Money;
import org.example.netty_basecamp.domains.coupon.domain.Coupon;
import org.example.netty_basecamp.domains.coupon.domain.CouponCreate;
import org.example.netty_basecamp.domains.coupon.domain.IssuedCoupon;
import org.example.netty_basecamp.domains.coupon.domain.service.CouponIssueDomainService;
import org.example.netty_basecamp.domains.coupon.domain.vo.IssuedCouponStatusEnum;
import org.example.netty_basecamp.domains.member.domain.Members;
import org.example.netty_basecamp.domains.member.domain.MembersCreate;
import org.example.netty_basecamp.fake.generator.FakeNumberGenerator;
import org.example.netty_basecamp.fake.generator.FakeTimeGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class CouponIssueDomainServiceTest {

    private TimeGenerator timeGenerator;
    private NumberGenerator numberGenerator;
    private CouponIssueDomainService couponIssueDomainService;

    @BeforeEach
    void setUp() {
        // TDD/단위 테스트 환경에서 도메인 서비스 객체 생성 (Spring Bean 아님)
        numberGenerator = new FakeNumberGenerator("123123");
        timeGenerator = new FakeTimeGenerator(1000L);

        couponIssueDomainService = new CouponIssueDomainService(numberGenerator, timeGenerator);
    }

    @Test
    @DisplayName("쿠폰 발급 정책에 따라 IssuedCoupon 엔티티가 정상적으로 조립되어 반환된다.")
    void 쿠폰을_멤버에게_발급하면_성공() {
        // Given (테스트를 위한 환경 구성)
        CouponCreate couponCreate = CouponCreate.builder()
                .description("테스트 생성입니다.")
                .totalCount(1000)
                .originalPrice(Money.of(10000))
                .expireDate(LocalDate.of(2027, 12, 31))
                .build();

        long currentTime = timeGenerator.millis();
        Coupon coupon = Coupon.from(couponCreate, currentTime);

        MembersCreate membersCreate = MembersCreate.builder()
                .name("이름")
                .address("주소")
                .age(11)
                .build();

        Members members = Members.create(membersCreate, timeGenerator.millis());

        // When (쿠폰 발급 도메인 서비스 실행)
        IssuedCoupon issuedCoupon = couponIssueDomainService.issueToMember(coupon, members.getId(), Money.of(10000));

        // Then (조립된 도메인 객체의 상태 및 정책 검증)
        Assertions.assertAll("IssuedCoupon 조립 검증",
                () -> Assertions.assertNotNull(issuedCoupon, "발급된 쿠폰 객체는 null이 아니어야 합니다."),

                // 1. 참조 데이터 매핑 검증
                () -> Assertions.assertEquals(coupon.getId(), issuedCoupon.getCouponId(), "원본 쿠폰의 ID가 일치해야 합니다."),
                () -> Assertions.assertEquals(members.getId(), issuedCoupon.getMemberId(), "발급 대상 멤버의 ID가 일치해야 합니다."),
                () -> Assertions.assertEquals(coupon.getOriginalPrice(), issuedCoupon.getAppliedPrice(), "적용된 가격은 원본 쿠폰의 가격과 일치해야 합니다."),

                // 2. 외부 의존성(Generator) 반환값 조립 검증
                () -> Assertions.assertEquals("123123", issuedCoupon.getCouponNumber(), "쿠폰 번호는 FakeNumberGenerator 정책을 따라야 합니다."),
                () -> Assertions.assertEquals(1000L, issuedCoupon.getIssuedAt(), "발급 시간은 FakeTimeGenerator 정책을 따라야 합니다."),

                // 3. 도메인 초기 상태 정책 검증
                () -> Assertions.assertEquals(IssuedCouponStatusEnum.UNUSED, issuedCoupon.getStatus(), "발급 직후의 쿠폰 상태는 UNUSED여야 합니다.")
        );
    }
}