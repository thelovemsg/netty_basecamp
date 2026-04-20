package org.example.netty_basecamp.simulator;

import org.example.netty_basecamp.cartracking.simulator.GpsInterpolator;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GpsInterpolatorTest {

    private final GpsInterpolator interpolator = new GpsInterpolator();

    @Test
    void 두_점_사이_보간_결과는_출발지로_시작하고_목적지로_끝난다() {
        Location origin = Location.of(37.50, 127.00);
        Location destination = Location.of(37.55, 127.05);

        List<Location> waypoints = interpolator.interpolate(origin, destination, 5);

        assertThat(waypoints.get(0)).isEqualTo(origin);
        assertThat(waypoints.get(waypoints.size() - 1)).isEqualTo(destination);
    }

    @Test
    void steps가_5이면_출발지와_도착지_포함_좌표_6개를_반환한다() {
        Location origin = Location.of(37.50, 127.00);
        Location destination = Location.of(37.55, 127.05);

        List<Location> waypoints = interpolator.interpolate(origin, destination, 5);

        assertThat(waypoints).hasSize(6); // 0~5 inclusive
    }

    @Test
    void 보간_좌표는_출발지에서_목적지_방향으로_이동한다() {
        Location origin = Location.of(37.50, 127.00);
        Location destination = Location.of(37.60, 127.10);

        List<Location> waypoints = interpolator.interpolate(origin, destination, 10);

        for (int i = 1; i < waypoints.size(); i++) {
            assertThat(waypoints.get(i).getLatitude())
                    .isGreaterThanOrEqualTo(waypoints.get(i - 1).getLatitude());
            assertThat(waypoints.get(i).getLongitude())
                    .isGreaterThanOrEqualTo(waypoints.get(i - 1).getLongitude());
        }
    }

    @Test
    void calculateSteps는_최소_3_최대_30을_반환한다() {
        // 매우 가까운 두 점
        int nearSteps = interpolator.calculateSteps(
                Location.of(37.50, 127.00), Location.of(37.501, 127.001));
        assertThat(nearSteps).isGreaterThanOrEqualTo(3);

        // 매우 먼 두 점
        int farSteps = interpolator.calculateSteps(
                Location.of(37.42, 126.80), Location.of(37.70, 127.18));
        assertThat(farSteps).isLessThanOrEqualTo(30);
    }
}
