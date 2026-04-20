package org.example.netty_basecamp.cartracking.tracking.domain.enums;

/** Journey(운행)의 생명주기 상태 — SCHEDULED → IN_PROGRESS → COMPLETED 순으로 단방향 전이 */
public enum JourneyStatusEnum {
    /** 배차 완료, 아직 출발 전 */
    SCHEDULED,
    /** 출발 후 목적지 도착 전, 위치 스냅샷 수집 중 */
    IN_PROGRESS,
    /** 목적지 도착 완료, 소요시간 확정 가능 */
    COMPLETED;
}
