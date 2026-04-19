package org.example.netty_basecamp.cartracking.tracking.domain;

import org.example.netty_basecamp.cartracking.tracking.domain.enums.JourneyStatusEnum;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.TrackingTarget;

public class Journey {

    private final Long id;
    private final TrackingTarget target;
    private final Location origin;
    private final Location destination;
    private final JourneyStatusEnum status;
    private final Long startedAt;
    private final Long arrivedAt;
    private final Long createdAt;
    private final Long modifiedAt;

    public Journey(Long id, TrackingTarget target, Location origin, Location destination,
                   JourneyStatusEnum status, Long startedAt, Long arrivedAt,
                   Long createdAt, Long modifiedAt) {
        this.id = id;
        this.target = target;
        this.origin = origin;
        this.destination = destination;
        this.status = status;
        this.startedAt = startedAt;
        this.arrivedAt = arrivedAt;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    public static Journey create(TrackingTarget target, Location origin, Location destination, long currentTime) {
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("출발지와 목적지가 같을 수 없습니다.");
        }
        return Journey.builder()
                .target(target)
                .origin(origin)
                .destination(destination)
                .status(JourneyStatusEnum.SCHEDULED)
                .createdAt(currentTime)
                .modifiedAt(currentTime)
                .build();
    }

    public Journey depart(long currentTime) {
        if (this.status != JourneyStatusEnum.SCHEDULED) {
            throw new IllegalStateException("배차 대기 상태에서만 출발할 수 있습니다.");
        }
        if (currentTime < this.createdAt) {
            throw new IllegalArgumentException("출발 시각은 배차 시각 이후여야 합니다.");
        }
        return Journey.builder()
                .id(this.id)
                .target(this.target)
                .origin(this.origin)
                .destination(this.destination)
                .status(JourneyStatusEnum.IN_PROGRESS)
                .startedAt(currentTime)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    public Journey arrive(long currentTime) {
        if (this.status != JourneyStatusEnum.IN_PROGRESS) {
            throw new IllegalStateException("운행 중인 상태에서만 도착할 수 있습니다.");
        }
        if (currentTime < this.startedAt) {
            throw new IllegalArgumentException("도착 시각은 출발 시각 이후여야 합니다.");
        }
        return Journey.builder()
                .id(this.id)
                .target(this.target)
                .origin(this.origin)
                .destination(this.destination)
                .status(JourneyStatusEnum.COMPLETED)
                .startedAt(this.startedAt)
                .arrivedAt(currentTime)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    public long getElapsed(long currentTime) {
        if (this.status == JourneyStatusEnum.SCHEDULED) {
            throw new IllegalStateException("출발 전인 운행은 경과시간을 계산할 수 없습니다.");
        }
        if (this.status == JourneyStatusEnum.COMPLETED) {
            return this.arrivedAt - this.startedAt;
        }
        if (currentTime < this.startedAt) {
            throw new IllegalArgumentException("조회 시각은 출발 시각 이후여야 합니다.");
        }
        return currentTime - this.startedAt;
    }

    public long getDuration() {
        if (this.status != JourneyStatusEnum.COMPLETED) {
            throw new IllegalStateException("완료된 운행만 소요시간을 계산할 수 있습니다.");
        }
        return this.arrivedAt - this.startedAt;
    }

    public Long getId() { return id; }
    public TrackingTarget getTarget() { return target; }
    public Location getOrigin() { return origin; }
    public Location getDestination() { return destination; }
    public JourneyStatusEnum getStatus() { return status; }
    public Long getStartedAt() { return startedAt; }
    public Long getArrivedAt() { return arrivedAt; }
    public Long getCreatedAt() { return createdAt; }
    public Long getModifiedAt() { return modifiedAt; }

    public static JourneyBuilder builder() { return new JourneyBuilder(); }

    public static class JourneyBuilder {
        private Long id;
        private TrackingTarget target;
        private Location origin;
        private Location destination;
        private JourneyStatusEnum status;
        private Long startedAt;
        private Long arrivedAt;
        private Long createdAt;
        private Long modifiedAt;

        JourneyBuilder() {}

        public JourneyBuilder id(Long id) { this.id = id; return this; }
        public JourneyBuilder target(TrackingTarget target) { this.target = target; return this; }
        public JourneyBuilder origin(Location origin) { this.origin = origin; return this; }
        public JourneyBuilder destination(Location destination) { this.destination = destination; return this; }
        public JourneyBuilder status(JourneyStatusEnum status) { this.status = status; return this; }
        public JourneyBuilder startedAt(Long startedAt) { this.startedAt = startedAt; return this; }
        public JourneyBuilder arrivedAt(Long arrivedAt) { this.arrivedAt = arrivedAt; return this; }
        public JourneyBuilder createdAt(Long createdAt) { this.createdAt = createdAt; return this; }
        public JourneyBuilder modifiedAt(Long modifiedAt) { this.modifiedAt = modifiedAt; return this; }

        public Journey build() {
            return new Journey(id, target, origin, destination, status, startedAt, arrivedAt, createdAt, modifiedAt);
        }
    }
}
