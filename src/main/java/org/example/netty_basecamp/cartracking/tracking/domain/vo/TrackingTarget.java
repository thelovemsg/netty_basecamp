package org.example.netty_basecamp.cartracking.tracking.domain.vo;

import org.example.netty_basecamp.cartracking.tracking.domain.enums.TrackingTargetTypeEnum;

import java.util.Objects;

public class TrackingTarget {

    private final Long targetId;
    private final TrackingTargetTypeEnum targetType;

    private TrackingTarget(Long targetId, TrackingTargetTypeEnum targetType) {
        this.targetId = targetId;
        this.targetType = targetType;
    }

    public static TrackingTarget of(Long targetId, TrackingTargetTypeEnum targetType) {
        return new TrackingTarget(targetId, targetType);
    }

    public Long getTargetId() { return targetId; }
    public TrackingTargetTypeEnum getTargetType() { return targetType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackingTarget that = (TrackingTarget) o;
        return Objects.equals(targetId, that.targetId) && targetType == that.targetType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetId, targetType);
    }
}
