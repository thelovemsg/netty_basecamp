package org.example.netty_basecamp.domains.coupon.domain.service;

import org.example.netty_basecamp.domains.common.service.NumberGenerator;
import org.example.netty_basecamp.domains.common.service.TimeGenerator;
import org.example.netty_basecamp.domains.common.vo.Money;
import org.example.netty_basecamp.domains.coupon.domain.Coupon;
import org.example.netty_basecamp.domains.coupon.domain.IssuedCoupon;

/**
 * 쿠폰 발급 도메인 서비스 (Domain Service)
 * * [도입 목적]
 * 1. 도메인 순수성 보장
 * - IssuedCoupon 엔티티 내부의 인프라성 의존성(Generator)을 제거하여, 순수한 상태 및 데이터 관리에만 집중하게 합니다.
 * 2. 테스트 용이성(TDD) 향상
 * - 엔티티 단위 테스트 시 복잡한 Mocking 없이, 생성된 결과 값(String, Long)만 주입하여 직관적인 검증이 가능합니다.
 * 3. 도메인 정책 캡슐화
 * - 번호 생성 및 발급 시간 통제라는 복합적인 '쿠폰 발급 규칙'을 하나의 서비스로 응집합니다.
 */
public class CouponIssueDomainService {

    private final NumberGenerator numberGenerator;
    private final TimeGenerator timeGenerator;

    public CouponIssueDomainService(NumberGenerator numberGenerator, TimeGenerator timeGenerator) {
        this.numberGenerator = numberGenerator;
        this.timeGenerator = timeGenerator;
    }

    /**
     * 쿠폰 발급에 대한 도메인 규칙을 캡슐화하여 IssuedCoupon 엔티티를 조립합니다.
     *
     * @param coupon 발급 대상 쿠폰 정책 엔티티
     * @param memberId 쿠폰을 발급받을 회원 ID
     * @return 조립이 완료된 IssuedCoupon 엔티티
     */
    public IssuedCoupon issueToMember(Coupon coupon, Long memberId, Money appliedPrice) {
        long currentTimeMillis = timeGenerator.millis();

        // 1. 쿠폰 수량 감소 로직 호출 (재고 소진 시 여기서 Exception 발생)
        coupon.reserve(currentTimeMillis);

        // 2. 발급 정책에 따른 값 생성
        String generatedNumber = numberGenerator.generate(coupon);

        // 3. 발급된 쿠폰 엔티티 생성
        return IssuedCoupon.issue(coupon, memberId, generatedNumber, currentTimeMillis, appliedPrice);
    }
}