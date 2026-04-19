package org.example.netty_basecamp.cartracking.tracking.domain.repository;

import org.example.netty_basecamp.cartracking.tracking.domain.LocationSnapshot;

import java.util.List;

public interface LocationSnapshotRepository {
    LocationSnapshot save(LocationSnapshot snapshot);
    List<LocationSnapshot> findAllByJourneyId(Long journeyId);
}
