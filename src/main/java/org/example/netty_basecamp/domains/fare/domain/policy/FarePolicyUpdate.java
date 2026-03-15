package org.example.netty_basecamp.domains.fare.domain.policy;

import java.math.BigDecimal;

public class FarePolicyUpdate {

    private final BigDecimal value;
    private final CalculationBasisEnum basis;
    private final int priority;

    public FarePolicyUpdate(BigDecimal value, CalculationBasisEnum basis, int priority) {
        this.value = value;
        this.basis = basis;
        this.priority = priority;
    }

    public BigDecimal getValue() { return value; }
    public CalculationBasisEnum getBasis() { return basis; }
    public int getPriority() { return priority; }

    public static FarePolicyUpdateBuilder builder() { return new FarePolicyUpdateBuilder(); }

    public static class FarePolicyUpdateBuilder {
        private BigDecimal value;
        private CalculationBasisEnum basis;
        private int priority;

        FarePolicyUpdateBuilder() {}

        public FarePolicyUpdateBuilder value(BigDecimal value) { this.value = value; return this; }
        public FarePolicyUpdateBuilder basis(CalculationBasisEnum basis) { this.basis = basis; return this; }
        public FarePolicyUpdateBuilder priority(int priority) { this.priority = priority; return this; }

        public FarePolicyUpdate build() {
            return new FarePolicyUpdate(value, basis, priority);
        }
    }
}