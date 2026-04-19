package org.example.netty_basecamp.cartracking.vehicle.domain;

import org.example.netty_basecamp.cartracking.vehicle.domain.dto.VehicleCreate;
import org.example.netty_basecamp.cartracking.vehicle.domain.enums.VehicleStatusEnum;
import org.example.netty_basecamp.cartracking.vehicle.domain.enums.VehicleTypeEnum;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;

public class Vehicle {

    private final Long id;
    private final String plateNumber;
    private final VehicleTypeEnum type;
    private final VehicleStatusEnum status;
    private final Location location;
    private final Long createdAt;
    private final Long modifiedAt;

    public Vehicle(Long id, String plateNumber, VehicleTypeEnum type, VehicleStatusEnum status,
                   Location location, Long createdAt, Long modifiedAt) {
        this.id = id;
        this.plateNumber = plateNumber;
        this.type = type;
        this.status = status;
        this.location = location;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    public static Vehicle create(VehicleCreate vehicleCreate, long currentTime) {
        return Vehicle.builder()
                .plateNumber(vehicleCreate.plateNumber())
                .type(vehicleCreate.type())
                .status(VehicleStatusEnum.AVAILABLE)
                .homeLocation(vehicleCreate.homeLocation())
                .createdAt(currentTime)
                .modifiedAt(currentTime)
                .build();
    }

    public Vehicle startTrip(long currentTime) {
        if (this.status == VehicleStatusEnum.ON_TRIP) {
            throw new IllegalStateException("이미 운행 중인 차량입니다.");
        }
        if (this.status == VehicleStatusEnum.OFFLINE) {
            throw new IllegalStateException("오프라인 상태의 차량은 운행을 시작할 수 없습니다.");
        }
        return Vehicle.builder()
                .id(this.id)
                .plateNumber(this.plateNumber)
                .type(this.type)
                .status(VehicleStatusEnum.ON_TRIP)
                .homeLocation(this.location)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    public Vehicle returnHome(long currentTime) {
        if (this.status != VehicleStatusEnum.ON_TRIP) {
            throw new IllegalStateException("운행 중인 차량만 복귀할 수 있습니다.");
        }
        return Vehicle.builder()
                .id(this.id)
                .plateNumber(this.plateNumber)
                .type(this.type)
                .status(VehicleStatusEnum.AVAILABLE)
                .homeLocation(this.location)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    public Vehicle goOffline(long currentTime) {
        if (this.status == VehicleStatusEnum.ON_TRIP) {
            throw new IllegalStateException("운행 중인 차량은 오프라인으로 전환할 수 없습니다.");
        }
        return Vehicle.builder()
                .id(this.id)
                .plateNumber(this.plateNumber)
                .type(this.type)
                .status(VehicleStatusEnum.OFFLINE)
                .homeLocation(this.location)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    public Long getId() { return id; }
    public String getPlateNumber() { return plateNumber; }
    public VehicleTypeEnum getType() { return type; }
    public VehicleStatusEnum getStatus() { return status; }
    public Location getLocation() { return location; }
    public Long getCreatedAt() { return createdAt; }
    public Long getModifiedAt() { return modifiedAt; }

    public static VehicleBuilder builder() { return new VehicleBuilder(); }

    public static class VehicleBuilder {
        private Long id;
        private String plateNumber;
        private VehicleTypeEnum type;
        private VehicleStatusEnum status;
        private Location homeLocation;
        private Long createdAt;
        private Long modifiedAt;

        VehicleBuilder() {}

        public VehicleBuilder id(Long id) { this.id = id; return this; }
        public VehicleBuilder plateNumber(String plateNumber) { this.plateNumber = plateNumber; return this; }
        public VehicleBuilder type(VehicleTypeEnum type) { this.type = type; return this; }
        public VehicleBuilder status(VehicleStatusEnum status) { this.status = status; return this; }
        public VehicleBuilder homeLocation(Location homeLocation) { this.homeLocation = homeLocation; return this; }
        public VehicleBuilder createdAt(Long createdAt) { this.createdAt = createdAt; return this; }
        public VehicleBuilder modifiedAt(Long modifiedAt) { this.modifiedAt = modifiedAt; return this; }

        public Vehicle build() {
            return new Vehicle(id, plateNumber, type, status, homeLocation, createdAt, modifiedAt);
        }
    }
}
