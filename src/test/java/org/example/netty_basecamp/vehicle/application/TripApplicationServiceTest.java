package org.example.netty_basecamp.vehicle.application;

import org.example.netty_basecamp.domains.vehicle.application.TripApplicationService;
import org.example.netty_basecamp.domains.vehicle.domain.vo.Location;
import org.example.netty_basecamp.domains.vehicle.domain.LocationSnapshot;
import org.example.netty_basecamp.domains.vehicle.domain.Trip;
import org.example.netty_basecamp.domains.vehicle.domain.enums.TripStatusEnum;
import org.example.netty_basecamp.domains.vehicle.domain.Vehicle;
import org.example.netty_basecamp.domains.vehicle.domain.dto.VehicleCreate;
import org.example.netty_basecamp.domains.vehicle.domain.enums.VehicleStatusEnum;
import org.example.netty_basecamp.domains.vehicle.domain.enums.VehicleTypeEnum;
import org.example.netty_basecamp.fake.generator.FakeTimeGenerator;
import org.example.netty_basecamp.fake.repository.FakeLocationSnapshotRepository;
import org.example.netty_basecamp.fake.repository.FakeTripRepository;
import org.example.netty_basecamp.fake.repository.FakeVehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripApplicationServiceTest {

    private TripApplicationService tripApplicationService;
    private FakeVehicleRepository vehicleRepository;
    private FakeTripRepository tripRepository;
    private FakeLocationSnapshotRepository snapshotRepository;

    private static final Location ORIGIN = Location.of(37.5012, 127.0396);
    private static final Location DESTINATION = Location.of(37.5665, 126.9780);
    private static final Location HOME = Location.of(37.5012, 127.0396);

    @BeforeEach
    void setUp() {
        vehicleRepository = new FakeVehicleRepository();
        tripRepository = new FakeTripRepository();
        snapshotRepository = new FakeLocationSnapshotRepository();

        tripApplicationService = new TripApplicationService(
                tripRepository,
                vehicleRepository,
                snapshotRepository,
                new FakeTimeGenerator(1000L)
        );
    }

    private Vehicle saveAvailableVehicle() {
        Vehicle vehicle = Vehicle.create(
                VehicleCreate.builder()
                        .plateNumber("12가3456")
                        .type(VehicleTypeEnum.SEDAN)
                        .homeLocation(HOME)
                        .build(),
                1000L
        );
        return vehicleRepository.save(vehicle);
    }

    // ========== scheduleTrip ==========

    @Nested
    @DisplayName("scheduleTrip — 배차")
    class ScheduleTrip {

        @Test
        @DisplayName("배차 시 SCHEDULED 상태의 Trip이 생성된다")
        void 배차_생성() {
            Vehicle vehicle = saveAvailableVehicle();

            Trip trip = tripApplicationService.scheduleTrip(vehicle.getId(), ORIGIN, DESTINATION);

            assertThat(trip.getId()).isNotNull();
            assertThat(trip.getVehicleId()).isEqualTo(vehicle.getId());
            assertThat(trip.getStatus()).isEqualTo(TripStatusEnum.SCHEDULED);
            assertThat(trip.getOrigin()).isEqualTo(ORIGIN);
            assertThat(trip.getDestination()).isEqualTo(DESTINATION);
            assertThat(trip.getStartedAt()).isNull();
        }

        @Test
        @DisplayName("배차 시 Vehicle 상태는 변경되지 않는다")
        void 배차시_차량_상태_유지() {
            Vehicle vehicle = saveAvailableVehicle();

            tripApplicationService.scheduleTrip(vehicle.getId(), ORIGIN, DESTINATION);

            Vehicle updated = vehicleRepository.findById(vehicle.getId());
            assertThat(updated.getStatus()).isEqualTo(VehicleStatusEnum.AVAILABLE);
        }

        @Test
        @DisplayName("존재하지 않는 차량으로 배차 시 예외가 발생한다")
        void 배차_차량없음_예외() {
            assertThatThrownBy(() -> tripApplicationService.scheduleTrip(999L, ORIGIN, DESTINATION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Vehicle not found: 999");
        }
    }

    // ========== departTrip ==========

    @Nested
    @DisplayName("departTrip — 출발")
    class DepartTrip {

        @Test
        @DisplayName("출발 시 Trip이 IN_PROGRESS가 되고 Vehicle이 ON_TRIP으로 전이된다")
        void 출발() {
            Vehicle vehicle = saveAvailableVehicle();
            tripApplicationService.scheduleTrip(vehicle.getId(), ORIGIN, DESTINATION);

            Trip departed = tripApplicationService.departTrip(vehicle.getId());

            assertThat(departed.getStatus()).isEqualTo(TripStatusEnum.IN_PROGRESS);
            assertThat(departed.getStartedAt()).isNotNull();

            Vehicle updated = vehicleRepository.findById(vehicle.getId());
            assertThat(updated.getStatus()).isEqualTo(VehicleStatusEnum.ON_TRIP);
        }

        @Test
        @DisplayName("배차된 운행이 없는 차량에 출발 시 예외가 발생한다")
        void 출발_배차없음_예외() {
            Vehicle vehicle = saveAvailableVehicle();

            assertThatThrownBy(() -> tripApplicationService.departTrip(vehicle.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("배차된 운행이 없습니다: vehicleId=" + vehicle.getId());
        }
    }

    // ========== recordSnapshot ==========

    @Nested
    @DisplayName("recordSnapshot — 스냅샷 기록")
    class RecordSnapshot {

        @Test
        @DisplayName("운행 중 스냅샷을 기록할 수 있다")
        void 스냅샷_기록() {
            Vehicle vehicle = saveAvailableVehicle();
            tripApplicationService.scheduleTrip(vehicle.getId(), ORIGIN, DESTINATION);
            tripApplicationService.departTrip(vehicle.getId());

            Location current = Location.of(37.5200, 127.0200);
            LocationSnapshot snapshot = tripApplicationService.recordSnapshot(vehicle.getId(), current);

            assertThat(snapshot.getId()).isNotNull();
            assertThat(snapshot.getVehicleId()).isEqualTo(vehicle.getId());
            assertThat(snapshot.getLocation()).isEqualTo(current);
        }

        @Test
        @DisplayName("진행 중인 운행이 없는 차량에 스냅샷 기록 시 예외가 발생한다")
        void 스냅샷_운행없음_예외() {
            Vehicle vehicle = saveAvailableVehicle();
            Location current = Location.of(37.5200, 127.0200);

            assertThatThrownBy(() -> tripApplicationService.recordSnapshot(vehicle.getId(), current))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ========== completeTrip ==========

    @Nested
    @DisplayName("completeTrip — 운행 완료")
    class CompleteTrip {

        @Test
        @DisplayName("운행 완료 시 Trip이 COMPLETED되고 Vehicle이 AVAILABLE로 전이된다")
        void 운행_완료() {
            Vehicle vehicle = saveAvailableVehicle();
            tripApplicationService.scheduleTrip(vehicle.getId(), ORIGIN, DESTINATION);
            tripApplicationService.departTrip(vehicle.getId());

            Trip completed = tripApplicationService.completeTrip(vehicle.getId());

            assertThat(completed.getStatus()).isEqualTo(TripStatusEnum.COMPLETED);
            assertThat(completed.getArrivedAt()).isNotNull();

            Vehicle updated = vehicleRepository.findById(vehicle.getId());
            assertThat(updated.getStatus()).isEqualTo(VehicleStatusEnum.AVAILABLE);
        }

        @Test
        @DisplayName("진행 중인 운행이 없는 차량을 완료 시 예외가 발생한다")
        void 운행완료_운행없음_예외() {
            Vehicle vehicle = saveAvailableVehicle();

            assertThatThrownBy(() -> tripApplicationService.completeTrip(vehicle.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ========== getTripRoute ==========

    @Nested
    @DisplayName("getTripRoute — 경로 조회")
    class GetTripRoute {

        @Test
        @DisplayName("운행 경로를 조회할 수 있다")
        void 경로_조회() {
            Vehicle vehicle = saveAvailableVehicle();
            Trip trip = tripApplicationService.scheduleTrip(vehicle.getId(), ORIGIN, DESTINATION);
            tripApplicationService.departTrip(vehicle.getId());

            tripApplicationService.recordSnapshot(vehicle.getId(), Location.of(37.51, 127.03));
            tripApplicationService.recordSnapshot(vehicle.getId(), Location.of(37.52, 127.02));
            tripApplicationService.recordSnapshot(vehicle.getId(), Location.of(37.53, 127.01));

            List<LocationSnapshot> route = tripApplicationService.getTripRoute(trip.getId());

            assertThat(route).hasSize(3);
            assertThat(route.get(0).getLocation()).isEqualTo(Location.of(37.51, 127.03));
            assertThat(route.get(2).getLocation()).isEqualTo(Location.of(37.53, 127.01));
        }
    }

    // ========== 전체 흐름 ==========

    @Nested
    @DisplayName("전체 흐름 — schedule → depart → record → complete")
    class FullLifecycle {

        @Test
        @DisplayName("배차부터 완료까지 전체 흐름이 정상 동작한다")
        void 전체_흐름() {
            Vehicle vehicle = saveAvailableVehicle();

            // 배차
            Trip scheduled = tripApplicationService.scheduleTrip(vehicle.getId(), ORIGIN, DESTINATION);
            assertThat(scheduled.getStatus()).isEqualTo(TripStatusEnum.SCHEDULED);
            assertThat(vehicleRepository.findById(vehicle.getId()).getStatus())
                    .isEqualTo(VehicleStatusEnum.AVAILABLE);

            // 출발
            Trip departed = tripApplicationService.departTrip(vehicle.getId());
            assertThat(departed.getStatus()).isEqualTo(TripStatusEnum.IN_PROGRESS);
            assertThat(vehicleRepository.findById(vehicle.getId()).getStatus())
                    .isEqualTo(VehicleStatusEnum.ON_TRIP);

            // 스냅샷 기록
            tripApplicationService.recordSnapshot(vehicle.getId(), Location.of(37.52, 127.02));

            // 완료
            Trip completed = tripApplicationService.completeTrip(vehicle.getId());
            assertThat(completed.getStatus()).isEqualTo(TripStatusEnum.COMPLETED);
            assertThat(vehicleRepository.findById(vehicle.getId()).getStatus())
                    .isEqualTo(VehicleStatusEnum.AVAILABLE);
        }
    }
}
