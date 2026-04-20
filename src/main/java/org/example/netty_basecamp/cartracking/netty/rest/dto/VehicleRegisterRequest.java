package org.example.netty_basecamp.cartracking.netty.rest.dto;

import org.example.netty_basecamp.cartracking.vehicle.domain.enums.VehicleTypeEnum;

/** HTTP 요청 body → VehicleCreate 변환용. Location VO 대신 double 사용 */
public record VehicleRegisterRequest(String plateNumber, VehicleTypeEnum type,
                                     double homeLat, double homeLng) {
}
