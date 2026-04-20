package org.example.netty_basecamp.cartracking.vehicle.domain.enums;

/** Vehicle(차량)의 운용 상태 — 배차 가능 여부와 상태 전이 규칙을 결정 */
public enum VehicleStatusEnum {
    /** 배차 대기 중, 새 운행 배정 가능 */
    AVAILABLE,
    /** 운행 중, 배차 불가 / 오프라인 전환 불가 */
    ON_TRIP,
    /** 운행 불가 상태(정비·휴무 등), 배차 및 출발 불가 */
    OFFLINE,
    /** 예약 확정, 아직 출발 전 (미래 확장용) */
    RESERVED;
}
