package org.example.netty_basecamp.domain.common.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class Money {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("금액은 0원 이상이어야 합니다.");
        }
        this.amount = amount.setScale(SCALE, ROUNDING);
    }

    public BigDecimal getAmount() { return amount; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    public static Money of(int amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("차감 후 금액은 0원 이상이어야 합니다.");
        }
        return new Money(result);
    }

    public Money discountByRate(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("할인율은 0~1 사이여야 합니다.");
        }
        return new Money(this.amount.multiply(BigDecimal.ONE.subtract(rate)));
    }

    public Money multiplyPercent(BigDecimal percent) {
        BigDecimal result = this.amount
                .multiply(percent)
                .divide(BigDecimal.valueOf(100), SCALE, ROUNDING);
        return new Money(result);
    }
}