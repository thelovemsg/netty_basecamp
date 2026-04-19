package org.example.netty_basecamp.tracking.domain;

import org.example.netty_basecamp.cartracking.tracking.domain.enums.TrackingTargetTypeEnum;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.TrackingTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingTargetTest {

    @Test
    @DisplayName("같은 targetId와 targetType이면 동등하다")
    void 동등성_비교() {
        TrackingTarget a = TrackingTarget.of(1L, TrackingTargetTypeEnum.VEHICLE);
        TrackingTarget b = TrackingTarget.of(1L, TrackingTargetTypeEnum.VEHICLE);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("targetId가 다르면 동등하지 않다")
    void targetId_다름() {
        TrackingTarget a = TrackingTarget.of(1L, TrackingTargetTypeEnum.VEHICLE);
        TrackingTarget b = TrackingTarget.of(2L, TrackingTargetTypeEnum.VEHICLE);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("targetId와 targetType이 올바르게 저장된다")
    void 필드_확인() {
        TrackingTarget target = TrackingTarget.of(10L, TrackingTargetTypeEnum.VEHICLE);

        assertThat(target.getTargetId()).isEqualTo(10L);
        assertThat(target.getTargetType()).isEqualTo(TrackingTargetTypeEnum.VEHICLE);
    }
}
