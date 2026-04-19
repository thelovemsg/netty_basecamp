package org.example.netty_basecamp.tracking.domain;

import org.example.netty_basecamp.cartracking.tracking.domain.Journey;
import org.example.netty_basecamp.cartracking.tracking.domain.enums.JourneyStatusEnum;
import org.example.netty_basecamp.cartracking.tracking.domain.enums.TrackingTargetTypeEnum;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.TrackingTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JourneyTest {

    private static final TrackingTarget TARGET = TrackingTarget.of(1L, TrackingTargetTypeEnum.VEHICLE);
    private static final Location ORIGIN = Location.of(37.5012, 127.0396);
    private static final Location DESTINATION = Location.of(37.5665, 126.9780);

    // ========== 생성 (Journey.create) ==========

    @Nested
    @DisplayName("Journey.create — 배차 생성")
    class Create {

        @Test
        @DisplayName("생성 시 SCHEDULED 상태로 시작한다")
        void SCHEDULED_상태() {
            Journey journey = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L);

            assertThat(journey.getTarget()).isEqualTo(TARGET);
            assertThat(journey.getOrigin()).isEqualTo(ORIGIN);
            assertThat(journey.getDestination()).isEqualTo(DESTINATION);
            assertThat(journey.getStatus()).isEqualTo(JourneyStatusEnum.SCHEDULED);
            assertThat(journey.getStartedAt()).isNull();
            assertThat(journey.getArrivedAt()).isNull();
            assertThat(journey.getId()).isNull();
        }

        @Test
        @DisplayName("createdAt과 modifiedAt이 설정된다")
        void 타임스탬프_일치() {
            Journey journey = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L);

            assertThat(journey.getCreatedAt()).isEqualTo(1000L);
            assertThat(journey.getModifiedAt()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("출발지와 목적지가 같으면 예외가 발생한다")
        void 출발지_목적지_동일_예외() {
            assertThatThrownBy(() -> Journey.create(TARGET, ORIGIN, ORIGIN, 1000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("출발지와 목적지가 같을 수 없습니다.");
        }

        @Test
        @DisplayName("위도만 다르고 경도가 같은 출발지/목적지는 허용된다")
        void 위도만_다른_경우_허용() {
            Location dest = Location.of(37.6000, 127.0396);

            Journey journey = Journey.create(TARGET, ORIGIN, dest, 1000L);

            assertThat(journey.getOrigin()).isNotEqualTo(journey.getDestination());
        }

        @Test
        @DisplayName("경도만 다르고 위도가 같은 출발지/목적지는 허용된다")
        void 경도만_다른_경우_허용() {
            Location dest = Location.of(37.5012, 127.0500);

            Journey journey = Journey.create(TARGET, ORIGIN, dest, 1000L);

            assertThat(journey.getOrigin()).isNotEqualTo(journey.getDestination());
        }
    }

    // ========== depart ==========

    @Nested
    @DisplayName("depart — 출발")
    class Depart {

        @Test
        @DisplayName("SCHEDULED → IN_PROGRESS로 전이되고 startedAt이 설정된다")
        void IN_PROGRESS_전이() {
            Journey journey = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L);

            Journey departed = journey.depart(2000L);

            assertThat(departed.getStatus()).isEqualTo(JourneyStatusEnum.IN_PROGRESS);
            assertThat(departed.getStartedAt()).isEqualTo(2000L);
            assertThat(departed.getModifiedAt()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("배차 시각과 동일한 시각에 출발할 수 있다")
        void 배차와_동시_출발_허용() {
            Journey journey = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L);

            Journey departed = journey.depart(1000L);

            assertThat(departed.getStatus()).isEqualTo(JourneyStatusEnum.IN_PROGRESS);
        }

        @Test
        @DisplayName("출발 시각이 배차 시각보다 이전이면 예외가 발생한다")
        void 출발시각_배차이전_예외() {
            Journey journey = Journey.create(TARGET, ORIGIN, DESTINATION, 5000L);

            assertThatThrownBy(() -> journey.depart(1000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("출발 시각은 배차 시각 이후여야 합니다.");
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서 depart 호출 시 예외가 발생한다")
        void IN_PROGRESS에서_출발_예외() {
            Journey departed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L).depart(2000L);

            assertThatThrownBy(() -> departed.depart(3000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("배차 대기 상태에서만 출발할 수 있습니다.");
        }

        @Test
        @DisplayName("COMPLETED 상태에서 depart 호출 시 예외가 발생한다")
        void COMPLETED에서_출발_예외() {
            Journey completed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L)
                    .depart(2000L).arrive(5000L);

            assertThatThrownBy(() -> completed.depart(6000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("배차 대기 상태에서만 출발할 수 있습니다.");
        }

        @Test
        @DisplayName("depart 후에도 origin, destination, target은 변경되지 않는다")
        void 불변_필드_유지() {
            Journey journey = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L);

            Journey departed = journey.depart(2000L);

            assertThat(departed.getTarget()).isEqualTo(TARGET);
            assertThat(departed.getOrigin()).isEqualTo(ORIGIN);
            assertThat(departed.getDestination()).isEqualTo(DESTINATION);
            assertThat(departed.getCreatedAt()).isEqualTo(1000L);
        }
    }

    // ========== arrive ==========

    @Nested
    @DisplayName("arrive — 운행 완료")
    class Arrive {

        @Test
        @DisplayName("IN_PROGRESS → COMPLETED로 전이되고 arrivedAt이 설정된다")
        void COMPLETED_전이() {
            Journey departed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L).depart(2000L);

            Journey completed = departed.arrive(5000L);

            assertThat(completed.getStatus()).isEqualTo(JourneyStatusEnum.COMPLETED);
            assertThat(completed.getArrivedAt()).isEqualTo(5000L);
            assertThat(completed.getModifiedAt()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("출발 시각과 동일한 시각에 도착할 수 있다")
        void 출발과_동시_도착_허용() {
            Journey departed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L).depart(2000L);

            Journey completed = departed.arrive(2000L);

            assertThat(completed.getStatus()).isEqualTo(JourneyStatusEnum.COMPLETED);
            assertThat(completed.getDuration()).isEqualTo(0L);
        }

        @Test
        @DisplayName("SCHEDULED 상태에서 arrive 호출 시 예외가 발생한다")
        void SCHEDULED에서_도착_예외() {
            Journey scheduled = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L);

            assertThatThrownBy(() -> scheduled.arrive(5000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("운행 중인 상태에서만 도착할 수 있습니다.");
        }

        @Test
        @DisplayName("이미 완료된 운행에 arrive 호출 시 예외가 발생한다")
        void 완료된_운행_재완료_예외() {
            Journey completed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L)
                    .depart(2000L).arrive(5000L);

            assertThatThrownBy(() -> completed.arrive(6000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("운행 중인 상태에서만 도착할 수 있습니다.");
        }

        @Test
        @DisplayName("도착 시각이 출발 시각보다 이전이면 예외가 발생한다")
        void 도착시각_출발이전_예외() {
            Journey departed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L).depart(5000L);

            assertThatThrownBy(() -> departed.arrive(3000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("도착 시각은 출발 시각 이후여야 합니다.");
        }

        @Test
        @DisplayName("arrive 후에도 origin, destination, target은 변경되지 않는다")
        void 불변_필드_유지() {
            Journey departed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L).depart(2000L);

            Journey completed = departed.arrive(5000L);

            assertThat(completed.getTarget()).isEqualTo(TARGET);
            assertThat(completed.getOrigin()).isEqualTo(ORIGIN);
            assertThat(completed.getDestination()).isEqualTo(DESTINATION);
            assertThat(completed.getStartedAt()).isEqualTo(2000L);
            assertThat(completed.getCreatedAt()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("id가 있는 Journey의 arrive 후에도 id는 유지된다")
        void id_유지() {
            Journey journey = Journey.builder()
                    .id(10L)
                    .target(TARGET)
                    .origin(ORIGIN)
                    .destination(DESTINATION)
                    .status(JourneyStatusEnum.IN_PROGRESS)
                    .startedAt(2000L)
                    .createdAt(1000L)
                    .modifiedAt(2000L)
                    .build();

            Journey completed = journey.arrive(5000L);

            assertThat(completed.getId()).isEqualTo(10L);
        }
    }

    // ========== getDuration ==========

    @Nested
    @DisplayName("getDuration — 소요시간 계산")
    class GetDuration {

        @Test
        @DisplayName("완료된 운행의 소요시간을 계산할 수 있다")
        void 소요시간_계산() {
            Journey completed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L)
                    .depart(2000L).arrive(6000L);

            assertThat(completed.getDuration()).isEqualTo(4000L);
        }

        @Test
        @DisplayName("동시 출발/도착 시 소요시간은 0이다")
        void 소요시간_0() {
            Journey completed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L)
                    .depart(2000L).arrive(2000L);

            assertThat(completed.getDuration()).isEqualTo(0L);
        }

        @Test
        @DisplayName("장시간 운행의 소요시간도 정확하게 계산된다")
        void 장시간_운행() {
            long oneHourMs = 3_600_000L;
            Journey completed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L)
                    .depart(2000L).arrive(2000L + oneHourMs);

            assertThat(completed.getDuration()).isEqualTo(oneHourMs);
        }

        @Test
        @DisplayName("SCHEDULED 상태의 소요시간 계산 시 예외가 발생한다")
        void SCHEDULED_소요시간_예외() {
            Journey journey = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L);

            assertThatThrownBy(journey::getDuration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("완료된 운행만 소요시간을 계산할 수 있습니다.");
        }

        @Test
        @DisplayName("IN_PROGRESS 상태의 소요시간 계산 시 예외가 발생한다")
        void 진행중_소요시간_예외() {
            Journey journey = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L).depart(2000L);

            assertThatThrownBy(journey::getDuration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("완료된 운행만 소요시간을 계산할 수 있습니다.");
        }
    }

    // ========== getElapsed ==========

    @Nested
    @DisplayName("getElapsed — 경과시간 계산")
    class GetElapsed {

        @Test
        @DisplayName("운행 중 경과시간을 계산할 수 있다")
        void 운행중_경과시간() {
            Journey departed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L).depart(2000L);

            assertThat(departed.getElapsed(5000L)).isEqualTo(3000L);
        }

        @Test
        @DisplayName("완료된 운행의 경과시간은 getDuration과 동일하다")
        void 완료_경과시간() {
            Journey completed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L)
                    .depart(2000L).arrive(6000L);

            assertThat(completed.getElapsed(9999L)).isEqualTo(4000L);
            assertThat(completed.getElapsed(9999L)).isEqualTo(completed.getDuration());
        }

        @Test
        @DisplayName("출발 직후 경과시간은 0이다")
        void 출발_직후_경과시간_0() {
            Journey departed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L).depart(2000L);

            assertThat(departed.getElapsed(2000L)).isEqualTo(0L);
        }

        @Test
        @DisplayName("SCHEDULED 상태에서 호출 시 예외가 발생한다")
        void SCHEDULED_경과시간_예외() {
            Journey scheduled = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L);

            assertThatThrownBy(() -> scheduled.getElapsed(5000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("출발 전인 운행은 경과시간을 계산할 수 없습니다.");
        }

        @Test
        @DisplayName("조회 시각이 출발 시각보다 이전이면 예외가 발생한다")
        void 조회시각_출발이전_예외() {
            Journey departed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L).depart(5000L);

            assertThatThrownBy(() -> departed.getElapsed(3000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("조회 시각은 출발 시각 이후여야 합니다.");
        }
    }

    // ========== 전체 생명주기 ==========

    @Nested
    @DisplayName("전체 생명주기")
    class Lifecycle {

        @Test
        @DisplayName("SCHEDULED → IN_PROGRESS → COMPLETED 정상 흐름")
        void 정상_흐름() {
            Journey journey = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L);
            assertThat(journey.getStatus()).isEqualTo(JourneyStatusEnum.SCHEDULED);

            Journey departed = journey.depart(2000L);
            assertThat(departed.getStatus()).isEqualTo(JourneyStatusEnum.IN_PROGRESS);

            Journey completed = departed.arrive(5000L);
            assertThat(completed.getStatus()).isEqualTo(JourneyStatusEnum.COMPLETED);
            assertThat(completed.getDuration()).isEqualTo(3000L);
        }
    }

    // ========== 불변성 ==========

    @Nested
    @DisplayName("불변성 보장")
    class Immutability {

        @Test
        @DisplayName("depart는 기존 객체를 변경하지 않고 새 객체를 반환한다")
        void depart_새객체() {
            Journey journey = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L);

            Journey departed = journey.depart(2000L);

            assertThat(departed).isNotSameAs(journey);
            assertThat(journey.getStatus()).isEqualTo(JourneyStatusEnum.SCHEDULED);
            assertThat(journey.getStartedAt()).isNull();
        }

        @Test
        @DisplayName("arrive는 기존 객체를 변경하지 않고 새 객체를 반환한다")
        void arrive_새객체() {
            Journey departed = Journey.create(TARGET, ORIGIN, DESTINATION, 1000L).depart(2000L);

            Journey completed = departed.arrive(5000L);

            assertThat(completed).isNotSameAs(departed);
            assertThat(departed.getStatus()).isEqualTo(JourneyStatusEnum.IN_PROGRESS);
            assertThat(departed.getArrivedAt()).isNull();
        }
    }
}
