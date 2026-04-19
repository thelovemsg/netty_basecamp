package org.example.netty_basecamp.vehicle.domain;

import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.example.netty_basecamp.cartracking.vehicle.domain.Vehicle;
import org.example.netty_basecamp.cartracking.vehicle.domain.dto.VehicleCreate;
import org.example.netty_basecamp.cartracking.vehicle.domain.enums.VehicleStatusEnum;
import org.example.netty_basecamp.cartracking.vehicle.domain.enums.VehicleTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleTest {

    private static final Location HOME = Location.of(37.5012, 127.0396);

    // ========== 생성 ==========

    @Nested
    @DisplayName("Vehicle 생성")
    class Create {

        @Test
        @DisplayName("VehicleCreate로 생성 시 AVAILABLE 상태로 시작한다")
        void AVAILABLE_상태로_생성() {
            VehicleCreate create = VehicleCreate.builder()
                    .plateNumber("12가3456")
                    .type(VehicleTypeEnum.SEDAN)
                    .homeLocation(HOME)
                    .build();

            Vehicle vehicle = Vehicle.create(create, 1000L);

            assertThat(vehicle.getPlateNumber()).isEqualTo("12가3456");
            assertThat(vehicle.getType()).isEqualTo(VehicleTypeEnum.SEDAN);
            assertThat(vehicle.getStatus()).isEqualTo(VehicleStatusEnum.AVAILABLE);
            assertThat(vehicle.getLocation()).isEqualTo(HOME);
            assertThat(vehicle.getCreatedAt()).isEqualTo(1000L);
            assertThat(vehicle.getModifiedAt()).isEqualTo(1000L);
            assertThat(vehicle.getId()).isNull();
        }

        @Test
        @DisplayName("SUV 타입으로 생성할 수 있다")
        void SUV_타입_생성() {
            Vehicle vehicle = Vehicle.create(
                    VehicleCreate.builder()
                            .plateNumber("34나5678")
                            .type(VehicleTypeEnum.SUV)
                            .homeLocation(HOME)
                            .build(),
                    1000L
            );

            assertThat(vehicle.getType()).isEqualTo(VehicleTypeEnum.SUV);
        }

        @Test
        @DisplayName("VAN 타입으로 생성할 수 있다")
        void VAN_타입_생성() {
            Vehicle vehicle = Vehicle.create(
                    VehicleCreate.builder()
                            .plateNumber("56다7890")
                            .type(VehicleTypeEnum.VAN)
                            .homeLocation(HOME)
                            .build(),
                    1000L
            );

            assertThat(vehicle.getType()).isEqualTo(VehicleTypeEnum.VAN);
        }
    }

    // ========== startTrip ==========

    @Nested
    @DisplayName("startTrip — 운행 시작")
    class StartTrip {

        @Test
        @DisplayName("AVAILABLE → ON_TRIP으로 전이된다")
        void AVAILABLE에서_ON_TRIP으로() {
            Vehicle vehicle = createAvailableVehicle("12가4567");

            Vehicle onTrip = vehicle.startTrip(2000L);

            assertThat(onTrip.getStatus()).isEqualTo(VehicleStatusEnum.ON_TRIP);
            assertThat(onTrip.getModifiedAt()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("ON_TRIP 상태에서 호출 시 예외가 발생한다")
        void ON_TRIP에서_재시작_예외() {
            Vehicle onTrip = createAvailableVehicle("12가4567").startTrip(2000L);

            assertThatThrownBy(() -> onTrip.startTrip(3000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("이미 운행 중인 차량입니다.");
        }

        @Test
        @DisplayName("OFFLINE 상태에서 호출 시 예외가 발생한다")
        void OFFLINE에서_시작_예외() {
            Vehicle offline = createAvailableVehicle("12가4567").goOffline(2000L);

            assertThatThrownBy(() -> offline.startTrip(3000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("오프라인 상태의 차량은 운행을 시작할 수 없습니다.");
        }

        @Test
        @DisplayName("plateNumber, type, homeLocation은 변경되지 않는다")
        void 불변_필드_유지() {
            Vehicle vehicle = createVehicleWithId(1L, "12가4567");

            Vehicle onTrip = vehicle.startTrip(2000L);

            assertThat(onTrip.getPlateNumber()).isEqualTo("12가4567");
            assertThat(onTrip.getType()).isEqualTo(VehicleTypeEnum.SEDAN);
            assertThat(onTrip.getLocation()).isEqualTo(HOME);
        }
    }

    // ========== returnHome ==========

    @Nested
    @DisplayName("returnHome — 복귀")
    class ReturnHome {

        @Test
        @DisplayName("ON_TRIP → AVAILABLE로 전이된다")
        void ON_TRIP에서_AVAILABLE로() {
            Vehicle onTrip = createAvailableVehicle("12가4567").startTrip(2000L);

            Vehicle returned = onTrip.returnHome(3000L);

            assertThat(returned.getStatus()).isEqualTo(VehicleStatusEnum.AVAILABLE);
            assertThat(returned.getModifiedAt()).isEqualTo(3000L);
        }

        @Test
        @DisplayName("AVAILABLE 상태에서 호출 시 예외가 발생한다")
        void AVAILABLE에서_복귀_예외() {
            Vehicle vehicle = createAvailableVehicle("12가4567");

            assertThatThrownBy(() -> vehicle.returnHome(2000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("운행 중인 차량만 복귀할 수 있습니다.");
        }

        @Test
        @DisplayName("OFFLINE 상태에서 호출 시 예외가 발생한다")
        void OFFLINE에서_복귀_예외() {
            Vehicle offline = createAvailableVehicle("12가4567").goOffline(2000L);

            assertThatThrownBy(() -> offline.returnHome(3000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("운행 중인 차량만 복귀할 수 있습니다.");
        }
    }

    // ========== goOffline ==========

    @Nested
    @DisplayName("goOffline — 오프라인 전환")
    class GoOffline {

        @Test
        @DisplayName("AVAILABLE → OFFLINE으로 전이된다")
        void AVAILABLE에서_OFFLINE으로() {
            Vehicle vehicle = createAvailableVehicle("12가4567");

            Vehicle offline = vehicle.goOffline(2000L);

            assertThat(offline.getStatus()).isEqualTo(VehicleStatusEnum.OFFLINE);
            assertThat(offline.getModifiedAt()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("ON_TRIP 상태에서 호출 시 예외가 발생한다")
        void ON_TRIP에서_오프라인_예외() {
            Vehicle onTrip = createAvailableVehicle("12가4567").startTrip(2000L);

            assertThatThrownBy(() -> onTrip.goOffline(3000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("운행 중인 차량은 오프라인으로 전환할 수 없습니다.");
        }

        @Test
        @DisplayName("이미 OFFLINE인 차량도 goOffline 호출이 가능하다")
        void OFFLINE에서_재호출_허용() {
            Vehicle offline = createAvailableVehicle("12가4567").goOffline(2000L);

            Vehicle stillOffline = offline.goOffline(3000L);

            assertThat(stillOffline.getStatus()).isEqualTo(VehicleStatusEnum.OFFLINE);
            assertThat(stillOffline.getModifiedAt()).isEqualTo(3000L);
        }
    }

    // ========== 전체 생명주기 ==========

    @Nested
    @DisplayName("전체 생명주기")
    class Lifecycle {

        @Test
        @DisplayName("AVAILABLE → ON_TRIP → AVAILABLE 정상 순환")
        void 운행_후_복귀() {
            Vehicle vehicle = createAvailableVehicle("12가4567");

            Vehicle onTrip = vehicle.startTrip(2000L);
            Vehicle returned = onTrip.returnHome(3000L);

            assertThat(returned.getStatus()).isEqualTo(VehicleStatusEnum.AVAILABLE);
        }

        @Test
        @DisplayName("AVAILABLE → ON_TRIP → AVAILABLE → ON_TRIP 반복 운행")
        void 반복_운행() {
            Vehicle vehicle = createAvailableVehicle("12가4567");

            Vehicle trip1 = vehicle.startTrip(2000L);
            Vehicle returned1 = trip1.returnHome(3000L);
            Vehicle trip2 = returned1.startTrip(4000L);
            Vehicle returned2 = trip2.returnHome(5000L);

            assertThat(returned2.getStatus()).isEqualTo(VehicleStatusEnum.AVAILABLE);
            assertThat(returned2.getModifiedAt()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("AVAILABLE → OFFLINE → AVAILABLE(복귀 불가, startTrip 불가)")
        void 오프라인_후_전이_제한() {
            Vehicle offline = createAvailableVehicle("12가4567").goOffline(2000L);

            assertThatThrownBy(() -> offline.startTrip(3000L))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> offline.returnHome(3000L))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ========== 불변성 ==========

    @Nested
    @DisplayName("불변성 보장")
    class Immutability {

        @Test
        @DisplayName("상태 전이는 기존 객체를 변경하지 않고 새 객체를 반환한다")
        void 새객체_반환() {
            Vehicle vehicle = createAvailableVehicle("12가4567");

            Vehicle onTrip = vehicle.startTrip(2000L);

            assertThat(onTrip).isNotSameAs(vehicle);
            assertThat(vehicle.getStatus()).isEqualTo(VehicleStatusEnum.AVAILABLE);
            assertThat(onTrip.getStatus()).isEqualTo(VehicleStatusEnum.ON_TRIP);
        }

        @Test
        @DisplayName("returnHome도 새 객체를 반환한다")
        void returnHome_새객체() {
            Vehicle onTrip = createAvailableVehicle("12가4567").startTrip(2000L);

            Vehicle returned = onTrip.returnHome(3000L);

            assertThat(returned).isNotSameAs(onTrip);
            assertThat(onTrip.getStatus()).isEqualTo(VehicleStatusEnum.ON_TRIP);
        }

        @Test
        @DisplayName("goOffline도 새 객체를 반환한다")
        void goOffline_새객체() {
            Vehicle vehicle = createAvailableVehicle("12가4567");

            Vehicle offline = vehicle.goOffline(2000L);

            assertThat(offline).isNotSameAs(vehicle);
            assertThat(vehicle.getStatus()).isEqualTo(VehicleStatusEnum.AVAILABLE);
        }

        @Test
        @DisplayName("상태 전이 후에도 id와 createdAt은 변경되지 않는다")
        void id_createdAt_불변() {
            Vehicle vehicle = createVehicleWithId(1L, "12가4567");

            Vehicle onTrip = vehicle.startTrip(2000L);
            Vehicle returned = onTrip.returnHome(3000L);

            assertThat(returned.getId()).isEqualTo(1L);
            assertThat(returned.getCreatedAt()).isEqualTo(1000L);
        }
    }

    // ========== 헬퍼 메서드 ==========

    private Vehicle createAvailableVehicle(String plateNumber) {
        return Vehicle.create(
                VehicleCreate.builder()
                        .plateNumber(plateNumber)
                        .type(VehicleTypeEnum.SEDAN)
                        .homeLocation(HOME)
                        .build(),
                1000L
        );
    }

    private Vehicle createVehicleWithId(Long id, String plateNumber) {
        return Vehicle.builder()
                .id(id)
                .plateNumber(plateNumber)
                .type(VehicleTypeEnum.SEDAN)
                .status(VehicleStatusEnum.AVAILABLE)
                .homeLocation(HOME)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();
    }
}
