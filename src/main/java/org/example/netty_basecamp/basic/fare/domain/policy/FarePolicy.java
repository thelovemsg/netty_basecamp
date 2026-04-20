package org.example.netty_basecamp.basic.fare.domain.policy;

import java.math.BigDecimal;

public class FarePolicy {

    private final Long id;
    private final Long fareId;
    private final FarePolicyTypeEnum type;
    private final BigDecimal value;
    private final CalculationBasisEnum basis;
    private final int priority;
    private final Long createdAt;
    private final Long modifiedAt;

    public FarePolicy(Long id, Long fareId, FarePolicyTypeEnum type, BigDecimal value,
                      CalculationBasisEnum basis, int priority, Long createdAt, Long modifiedAt) {
        validateValue(type, value);
        this.id = id;
        this.fareId = fareId;
        this.type = type;
        this.value = value;
        this.basis = basis;
        this.priority = priority;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    // 정책 최초 생성
    public static FarePolicy from(FarePolicyCreate create, Long fareId, long currentTime) {
        return FarePolicy.builder()
                .fareId(fareId)
                .type(create.getType())
                .value(create.getValue())
                .basis(create.getBasis())
                .priority(create.getPriority())
                .createdAt(currentTime)
                .modifiedAt(currentTime)
                .build();
    }

    public FarePolicy update(FarePolicyUpdate update, long currentTime) {
        return FarePolicy.builder()
                .id(this.id)
                .fareId(this.fareId)
                .type(this.type)
                .value(update.getValue())
                .basis(update.getBasis())
                .priority(update.getPriority())
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    // 생성자 검증
    private static void validateValue(FarePolicyTypeEnum type, BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("정책 값은 필수입니다.");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("정책 값은 0 이상이어야 합니다.");
        }
        if (type.isPercentType() && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("퍼센트 값은 100을 초과할 수 없습니다.");
        }
    }

    public Long getId() { return id; }
    public Long getFareId() { return fareId; }
    public FarePolicyTypeEnum getType() { return type; }
    public BigDecimal getValue() { return value; }
    public CalculationBasisEnum getBasis() { return basis; }
    public int getPriority() { return priority; }
    public Long getCreatedAt() { return createdAt; }
    public Long getModifiedAt() { return modifiedAt; }

    public static FarePolicyBuilder builder() { return new FarePolicyBuilder(); }

    public static class FarePolicyBuilder {
        private Long id;
        private Long fareId;
        private FarePolicyTypeEnum type;
        private BigDecimal value;
        private CalculationBasisEnum basis;
        private int priority;
        private Long createdAt;
        private Long modifiedAt;

        FarePolicyBuilder() {}

        public FarePolicyBuilder id(Long id) { this.id = id; return this; }
        public FarePolicyBuilder fareId(Long fareId) { this.fareId = fareId; return this; }
        public FarePolicyBuilder type(FarePolicyTypeEnum type) { this.type = type; return this; }
        public FarePolicyBuilder value(BigDecimal value) { this.value = value; return this; }
        public FarePolicyBuilder basis(CalculationBasisEnum basis) { this.basis = basis; return this; }
        public FarePolicyBuilder priority(int priority) { this.priority = priority; return this; }
        public FarePolicyBuilder createdAt(Long createdAt) { this.createdAt = createdAt; return this; }
        public FarePolicyBuilder modifiedAt(Long modifiedAt) { this.modifiedAt = modifiedAt; return this; }

        public FarePolicy build() {
            return new FarePolicy(id, fareId, type, value, basis, priority, createdAt, modifiedAt);
        }
    }

}