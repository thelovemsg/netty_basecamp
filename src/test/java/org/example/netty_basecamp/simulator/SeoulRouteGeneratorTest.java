package org.example.netty_basecamp.simulator;

import org.example.netty_basecamp.cartracking.simulator.SeoulRouteGenerator;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SeoulRouteGeneratorTest {

    private final SeoulRouteGenerator generator = new SeoulRouteGenerator();

    @Test
    void 생성된_좌표는_서울_바운딩_박스_내에_있다() {
        for (int i = 0; i < 100; i++) {
            Location loc = generator.randomLocation();
            assertThat(loc.getLatitude()).isBetween(BigDecimal.valueOf(37.42), BigDecimal.valueOf(37.70));
            assertThat(loc.getLongitude()).isBetween(BigDecimal.valueOf(126.80), BigDecimal.valueOf(127.18));
        }
    }

    @Test
    void 출발지와_목적지는_서로_다르다() {
        for (int i = 0; i < 50; i++) {
            Location[] route = generator.randomRoute();
            assertThat(route[0]).isNotEqualTo(route[1]);
        }
    }
}
