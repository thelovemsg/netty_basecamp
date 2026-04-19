package org.example.netty_basecamp.tracking.domain;

import org.example.netty_basecamp.cartracking.tracking.domain.LocationSnapshot;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocationSnapshotTest {

    private static final Location DEFAULT_LOCATION = Location.of(37.5012, 127.0396);

    // ========== 생성 (capture) ==========

    @Nested
    @DisplayName("LocationSnapshot.capture — 스냅샷 생성")
    class Capture {

        @Test
        @DisplayName("생성 시 정보가 올바르게 저장된다")
        void 정상_생성() {
            LocationSnapshot snapshot = LocationSnapshot.capture(1L, DEFAULT_LOCATION, 1000L);

            assertThat(snapshot.getJourneyId()).isEqualTo(1L);
            assertThat(snapshot.getLocation()).isEqualTo(DEFAULT_LOCATION);
            assertThat(snapshot.getCapturedAt()).isEqualTo(1000L);
            assertThat(snapshot.getId()).isNull();
        }

        @Test
        @DisplayName("서로 다른 위치의 스냅샷은 location이 다르다")
        void 다른_위치_스냅샷() {
            Location locationA = Location.of(37.5012, 127.0396);
            Location locationB = Location.of(37.5200, 127.0200);

            LocationSnapshot snapshotA = LocationSnapshot.capture(1L, locationA, 1000L);
            LocationSnapshot snapshotB = LocationSnapshot.capture(1L, locationB, 2000L);

            assertThat(snapshotA.getLocation()).isNotEqualTo(snapshotB.getLocation());
        }
    }

    // ========== 연속 스냅샷 시나리오 ==========

    @Nested
    @DisplayName("연속 스냅샷 시나리오")
    class ConsecutiveSnapshots {

        @Test
        @DisplayName("같은 journeyId로 여러 스냅샷을 생성할 수 있다")
        void 같은_journey_여러_스냅샷() {
            LocationSnapshot s1 = LocationSnapshot.capture(1L, Location.of(37.50, 127.03), 1000L);
            LocationSnapshot s2 = LocationSnapshot.capture(1L, Location.of(37.51, 127.02), 2000L);
            LocationSnapshot s3 = LocationSnapshot.capture(1L, Location.of(37.52, 127.01), 3000L);

            assertThat(s1.getJourneyId()).isEqualTo(s2.getJourneyId()).isEqualTo(s3.getJourneyId());
            assertThat(s1.getCapturedAt()).isLessThan(s2.getCapturedAt());
            assertThat(s2.getCapturedAt()).isLessThan(s3.getCapturedAt());
        }

        @Test
        @DisplayName("같은 위치에서 연속 스냅샷이 찍히면 정차 상태를 의미한다")
        void 연속_정차_감지() {
            LocationSnapshot s1 = LocationSnapshot.capture(1L, DEFAULT_LOCATION, 1000L);
            LocationSnapshot s2 = LocationSnapshot.capture(1L, DEFAULT_LOCATION, 2000L);
            LocationSnapshot s3 = LocationSnapshot.capture(1L, DEFAULT_LOCATION, 3000L);

            assertThat(s1.getLocation()).isEqualTo(s2.getLocation()).isEqualTo(s3.getLocation());
        }
    }
}
