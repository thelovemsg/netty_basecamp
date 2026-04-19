package org.example.netty_basecamp.domains.vehicle.domain;

import org.example.netty_basecamp.domains.vehicle.domain.vo.Location;

public class LocationSnapshot {

    private final Long id;
    private final Long tripId;
    private final Long vehicleId;
    private final Location location;
    private final Long capturedAt;

    public LocationSnapshot(Long id, Long tripId, Long vehicleId, Location location,
                            Long capturedAt) {
        this.id = id;
        this.tripId = tripId;
        this.vehicleId = vehicleId;
        this.location = location;
        this.capturedAt = capturedAt;
    }

    public static LocationSnapshot capture(Long tripId, Long vehicleId, Location location,
                                           long currentTime) {
        return LocationSnapshot.builder()
                .tripId(tripId)
                .vehicleId(vehicleId)
                .location(location)
                .capturedAt(currentTime)
                .build();
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Long getVehicleId() { return vehicleId; }
    public Location getLocation() { return location; }
    public Long getCapturedAt() { return capturedAt; }

    public static LocationSnapshotBuilder builder() { return new LocationSnapshotBuilder(); }

    public static class LocationSnapshotBuilder {
        private Long id;
        private Long tripId;
        private Long vehicleId;
        private Location location;
        private Long capturedAt;

        LocationSnapshotBuilder() {}

        public LocationSnapshotBuilder id(Long id) { this.id = id; return this; }
        public LocationSnapshotBuilder tripId(Long tripId) { this.tripId = tripId; return this; }
        public LocationSnapshotBuilder vehicleId(Long vehicleId) { this.vehicleId = vehicleId; return this; }
        public LocationSnapshotBuilder location(Location location) { this.location = location; return this; }
        public LocationSnapshotBuilder capturedAt(Long capturedAt) { this.capturedAt = capturedAt; return this; }

        public LocationSnapshot build() {
            return new LocationSnapshot(id, tripId, vehicleId, location, capturedAt);
        }
    }
}
