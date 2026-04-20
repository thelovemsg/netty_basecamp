package org.example.netty_basecamp.cartracking.netty.rest.dto;

/** 운행 배차 요청 body */
public record ScheduleTripRequest(Long vehicleId,
                                  double originLat, double originLng,
                                  double destLat, double destLng) {
}
