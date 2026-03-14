package org.example.netty_basecamp.domain.fare.domain;

import org.example.netty_basecamp.domain.common.vo.Money;

public record FareCreate(String name, Money basePrice) {

    public static FareCreateBuilder builder() {
        return new FareCreateBuilder();
    }

    public static class FareCreateBuilder {
        private String name;
        private Money basePrice;

        FareCreateBuilder() {
        }

        public FareCreateBuilder name(String name) {
            this.name = name;
            return this;
        }

        public FareCreateBuilder basePrice(Money basePrice) {
            this.basePrice = basePrice;
            return this;
        }

        public FareCreate build() {
            return new FareCreate(name, basePrice);
        }
    }
}
