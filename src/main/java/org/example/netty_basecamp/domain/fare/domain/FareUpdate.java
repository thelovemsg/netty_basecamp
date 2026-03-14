package org.example.netty_basecamp.domain.fare.domain;

import org.example.netty_basecamp.domain.common.vo.Money;

public record FareUpdate(Long id, String name, Money basePrice) {

    public static FareUpdateBuilder builder() {
        return new FareUpdateBuilder();
    }

    public static class FareUpdateBuilder {
        private Long id;
        private String name;
        private Money basePrice;

        FareUpdateBuilder() {
        }

        public FareUpdateBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public FareUpdateBuilder name(String name) {
            this.name = name;
            return this;
        }

        public FareUpdateBuilder basePrice(Money basePrice) {
            this.basePrice = basePrice;
            return this;
        }

        public FareUpdate build() {
            return new FareUpdate(id, name, basePrice);
        }
    }
}
