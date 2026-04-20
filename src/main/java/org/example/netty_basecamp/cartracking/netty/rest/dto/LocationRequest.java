package org.example.netty_basecamp.cartracking.netty.rest.dto;

/** 위치 스냅샷 기록 요청 body */
public record LocationRequest(double lat, double lng) {
}
