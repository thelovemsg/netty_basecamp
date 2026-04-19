package org.example.netty_basecamp.domains.vehicle.domain.dto;

import org.example.netty_basecamp.domains.vehicle.domain.vo.Location;
import org.example.netty_basecamp.domains.vehicle.domain.enums.VehicleTypeEnum;

public record VehicleCreate(String plateNumber, VehicleTypeEnum type, Location homeLocation) {

    public static VehicleCreateBuilder builder() { return new VehicleCreateBuilder(); }

    public static class VehicleCreateBuilder {
        private String plateNumber;
        private VehicleTypeEnum type;
        private Location homeLocation;

        VehicleCreateBuilder() {}

        public VehicleCreateBuilder plateNumber(String plateNumber) { this.plateNumber = plateNumber; return this; }
        public VehicleCreateBuilder type(VehicleTypeEnum type) { this.type = type; return this; }
        public VehicleCreateBuilder homeLocation(Location homeLocation) { this.homeLocation = homeLocation; return this; }

        public VehicleCreate build() {
            return new VehicleCreate(plateNumber, type, homeLocation);
        }
    }
}
