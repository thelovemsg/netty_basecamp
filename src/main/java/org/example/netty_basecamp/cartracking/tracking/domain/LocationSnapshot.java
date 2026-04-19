package org.example.netty_basecamp.cartracking.tracking.domain;

import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;

public class LocationSnapshot {

    private final Long id;
    private final Long journeyId;
    private final Location location;
    private final Long capturedAt;

    public LocationSnapshot(Long id, Long journeyId, Location location, Long capturedAt) {
        this.id = id;
        this.journeyId = journeyId;
        this.location = location;
        this.capturedAt = capturedAt;
    }

    public static LocationSnapshot capture(Long journeyId, Location location, long currentTime) {
        return LocationSnapshot.builder()
                .journeyId(journeyId)
                .location(location)
                .capturedAt(currentTime)
                .build();
    }

    public Long getId() { return id; }
    public Long getJourneyId() { return journeyId; }
    public Location getLocation() { return location; }
    public Long getCapturedAt() { return capturedAt; }

    public static LocationSnapshotBuilder builder() { return new LocationSnapshotBuilder(); }

    public static class LocationSnapshotBuilder {
        private Long id;
        private Long journeyId;
        private Location location;
        private Long capturedAt;

        LocationSnapshotBuilder() {}

        public LocationSnapshotBuilder id(Long id) { this.id = id; return this; }
        public LocationSnapshotBuilder journeyId(Long journeyId) { this.journeyId = journeyId; return this; }
        public LocationSnapshotBuilder location(Location location) { this.location = location; return this; }
        public LocationSnapshotBuilder capturedAt(Long capturedAt) { this.capturedAt = capturedAt; return this; }

        public LocationSnapshot build() {
            return new LocationSnapshot(id, journeyId, location, capturedAt);
        }
    }
}
