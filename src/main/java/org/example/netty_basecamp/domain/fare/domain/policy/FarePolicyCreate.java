package org.example.netty_basecamp.domain.fare.domain.policy;

import java.math.BigDecimal;

public class FarePolicyCreate {

    private final FarePolicyTypeEnum type;
    private final BigDecimal value;
    private final CalculationBasisEnum basis;
    private final int priority;

    public FarePolicyCreate(FarePolicyTypeEnum type, BigDecimal value, CalculationBasisEnum basis, int priority) {
        this.type = type;
        this.value = value;
        this.basis = basis;
        this.priority = priority;
    }

    public FarePolicyTypeEnum getType() { return type; }
    public BigDecimal getValue() { return value; }
    public CalculationBasisEnum getBasis() { return basis; }
    public int getPriority() { return priority; }

    public static FarePolicyCreateBuilder builder() { return new FarePolicyCreateBuilder(); }

    public static class FarePolicyCreateBuilder {
        private FarePolicyTypeEnum type;
        private BigDecimal value;
        private CalculationBasisEnum basis;
        private int priority;

        FarePolicyCreateBuilder() {}

        public FarePolicyCreateBuilder type(FarePolicyTypeEnum type) { this.type = type; return this; }
        public FarePolicyCreateBuilder value(BigDecimal value) { this.value = value; return this; }
        public FarePolicyCreateBuilder basis(CalculationBasisEnum basis) { this.basis = basis; return this; }
        public FarePolicyCreateBuilder priority(int priority) { this.priority = priority; return this; }

        public FarePolicyCreate build() {
            return new FarePolicyCreate(type, value, basis, priority);
        }
    }
}
