package org.example.netty_basecamp.domains.vehicle.domain.repository;

import org.example.netty_basecamp.domains.vehicle.domain.LocationSnapshot;

import java.util.List;

public interface LocationSnapshotRepository {
    LocationSnapshot save(LocationSnapshot snapshot);
    List<LocationSnapshot> findAllByTripId(Long tripId);
}
