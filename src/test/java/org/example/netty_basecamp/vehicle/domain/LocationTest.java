package org.example.netty_basecamp.vehicle.domain;

import org.example.netty_basecamp.domains.vehicle.domain.vo.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationTest {

    @Test
    @DisplayName("Location 생성 시 위도와 경도가 scale 6으로 저장된다")
    void Location_생성() {
        Location location = Location.of(37.5012, 127.0396);

        assertThat(location.getLatitude()).isEqualByComparingTo(new BigDecimal("37.501200"));
        assertThat(location.getLongitude()).isEqualByComparingTo(new BigDecimal("127.039600"));
        assertThat(location.getLatitude().scale()).isEqualTo(6);
        assertThat(location.getLongitude().scale()).isEqualTo(6);
    }

    @Test
    @DisplayName("BigDecimal 팩토리 메서드로 생성할 수 있다")
    void BigDecimal_팩토리() {
        Location location = Location.of(new BigDecimal("37.501200"), new BigDecimal("127.039600"));

        assertThat(location.getLatitude()).isEqualByComparingTo(new BigDecimal("37.501200"));
        assertThat(location.getLongitude()).isEqualByComparingTo(new BigDecimal("127.039600"));
    }

    @Test
    @DisplayName("위도가 -90 미만이면 예외가 발생한다")
    void 위도_하한_초과() {
        assertThatThrownBy(() -> Location.of(-91, 127.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("위도는 -90 ~ 90 범위여야 합니다.");
    }

    @Test
    @DisplayName("위도가 90 초과이면 예외가 발생한다")
    void 위도_상한_초과() {
        assertThatThrownBy(() -> Location.of(91, 127.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("위도는 -90 ~ 90 범위여야 합니다.");
    }

    @Test
    @DisplayName("경도가 -180 미만이면 예외가 발생한다")
    void 경도_하한_초과() {
        assertThatThrownBy(() -> Location.of(37.5, -181))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("경도는 -180 ~ 180 범위여야 합니다.");
    }

    @Test
    @DisplayName("경도가 180 초과이면 예외가 발생한다")
    void 경도_상한_초과() {
        assertThatThrownBy(() -> Location.of(37.5, 181))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("경도는 -180 ~ 180 범위여야 합니다.");
    }

    @Test
    @DisplayName("같은 좌표의 Location은 동등하다")
    void 동등성_비교() {
        Location a = Location.of(37.5012, 127.0396);
        Location b = Location.of(37.5012, 127.0396);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("다른 좌표의 Location은 동등하지 않다")
    void 비동등성_비교() {
        Location a = Location.of(37.5012, 127.0396);
        Location b = Location.of(37.5013, 127.0396);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("소수점 7자리 이상은 6자리로 반올림된다")
    void 정밀도_반올림() {
        Location location = Location.of(37.5012345, 127.0);

        assertThat(location.getLatitude()).isEqualByComparingTo(new BigDecimal("37.501235"));
    }
}
