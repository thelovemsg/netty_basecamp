package org.example.netty_basecamp.domains.vehicle.domain;

import org.example.netty_basecamp.domains.vehicle.domain.enums.TripStatusEnum;
import org.example.netty_basecamp.domains.vehicle.domain.vo.Location;

public class Trip {

    private final Long id;
    private final Long vehicleId;
    private final Location origin;
    private final Location destination;
    private final TripStatusEnum status;
    private final Long startedAt;
    private final Long arrivedAt;
    private final Long createdAt;
    private final Long modifiedAt;

    public Trip(Long id, Long vehicleId, Location origin, Location destination,
                TripStatusEnum status, Long startedAt, Long arrivedAt,
                Long createdAt, Long modifiedAt) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.origin = origin;
        this.destination = destination;
        this.status = status;
        this.startedAt = startedAt;
        this.arrivedAt = arrivedAt;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    public static Trip create(Long vehicleId, Location origin, Location destination, long currentTime) {
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("출발지와 목적지가 같을 수 없습니다.");
        }
        return Trip.builder()
                .vehicleId(vehicleId)
                .origin(origin)
                .destination(destination)
                .status(TripStatusEnum.SCHEDULED)
                .createdAt(currentTime)
                .modifiedAt(currentTime)
                .build();
    }

    public Trip depart(long currentTime) {
        if (this.status != TripStatusEnum.SCHEDULED) {
            throw new IllegalStateException("배차 대기 상태에서만 출발할 수 있습니다.");
        }
        if (currentTime < this.createdAt) {
            throw new IllegalArgumentException("출발 시각은 배차 시각 이후여야 합니다.");
        }
        return Trip.builder()
                .id(this.id)
                .vehicleId(this.vehicleId)
                .origin(this.origin)
                .destination(this.destination)
                .status(TripStatusEnum.IN_PROGRESS)
                .startedAt(currentTime)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    public Trip arrive(long currentTime) {
        if (this.status != TripStatusEnum.IN_PROGRESS) {
            throw new IllegalStateException("운행 중인 상태에서만 도착할 수 있습니다.");
        }
        if (currentTime < this.startedAt) {
            throw new IllegalArgumentException("도착 시각은 출발 시각 이후여야 합니다.");
        }
        return Trip.builder()
                .id(this.id)
                .vehicleId(this.vehicleId)
                .origin(this.origin)
                .destination(this.destination)
                .status(TripStatusEnum.COMPLETED)
                .startedAt(this.startedAt)
                .arrivedAt(currentTime)
                .createdAt(this.createdAt)
                .modifiedAt(currentTime)
                .build();
    }

    public long getElapsed(long currentTime) {
        if (this.status == TripStatusEnum.SCHEDULED) {
            throw new IllegalStateException("출발 전인 운행은 경과시간을 계산할 수 없습니다.");
        }
        if (this.status == TripStatusEnum.COMPLETED) {
            return this.arrivedAt - this.startedAt;
        }
        if (currentTime < this.startedAt) {
            throw new IllegalArgumentException("조회 시각은 출발 시각 이후여야 합니다.");
        }
        return currentTime - this.startedAt;
    }

    public long getDuration() {
        if (this.status != TripStatusEnum.COMPLETED) {
            throw new IllegalStateException("완료된 운행만 소요시간을 계산할 수 있습니다.");
        }
        return this.arrivedAt - this.startedAt;
    }

    public Long getId() { return id; }
    public Long getVehicleId() { return vehicleId; }
    public Location getOrigin() { return origin; }
    public Location getDestination() { return destination; }
    public TripStatusEnum getStatus() { return status; }
    public Long getStartedAt() { return startedAt; }
    public Long getArrivedAt() { return arrivedAt; }
    public Long getCreatedAt() { return createdAt; }
    public Long getModifiedAt() { return modifiedAt; }

    public static TripBuilder builder() { return new TripBuilder(); }

    public static class TripBuilder {
        private Long id;
        private Long vehicleId;
        private Location origin;
        private Location destination;
        private TripStatusEnum status;
        private Long startedAt;
        private Long arrivedAt;
        private Long createdAt;
        private Long modifiedAt;

        TripBuilder() {}

        public TripBuilder id(Long id) { this.id = id; return this; }
        public TripBuilder vehicleId(Long vehicleId) { this.vehicleId = vehicleId; return this; }
        public TripBuilder origin(Location origin) { this.origin = origin; return this; }
        public TripBuilder destination(Location destination) { this.destination = destination; return this; }
        public TripBuilder status(TripStatusEnum status) { this.status = status; return this; }
        public TripBuilder startedAt(Long startedAt) { this.startedAt = startedAt; return this; }
        public TripBuilder arrivedAt(Long arrivedAt) { this.arrivedAt = arrivedAt; return this; }
        public TripBuilder createdAt(Long createdAt) { this.createdAt = createdAt; return this; }
        public TripBuilder modifiedAt(Long modifiedAt) { this.modifiedAt = modifiedAt; return this; }

        public Trip build() {
            return new Trip(id, vehicleId, origin, destination, status, startedAt, arrivedAt, createdAt, modifiedAt);
        }
    }
}
