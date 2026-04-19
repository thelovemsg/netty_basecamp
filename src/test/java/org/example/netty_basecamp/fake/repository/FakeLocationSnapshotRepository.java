package org.example.netty_basecamp.fake.repository;

import org.example.netty_basecamp.domains.vehicle.domain.LocationSnapshot;
import org.example.netty_basecamp.domains.vehicle.domain.repository.LocationSnapshotRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FakeLocationSnapshotRepository implements LocationSnapshotRepository {

    private final Map<Long, LocationSnapshot> store = new HashMap<>();
    private long sequence = 1;

    @Override
    public LocationSnapshot save(LocationSnapshot snapshot) {
        if (snapshot.getId() == null) {
            Long newId = sequence++;
            LocationSnapshot withId = LocationSnapshot.builder()
                    .id(newId)
                    .tripId(snapshot.getTripId())
                    .vehicleId(snapshot.getVehicleId())
                    .location(snapshot.getLocation())
                    .capturedAt(snapshot.getCapturedAt())
                    .build();
            store.put(newId, withId);
            return withId;
        }
        store.put(snapshot.getId(), snapshot);
        return snapshot;
    }

    @Override
    public List<LocationSnapshot> findAllByTripId(Long tripId) {
        return store.values().stream()
                .filter(s -> s.getTripId().equals(tripId))
                .sorted((a, b) -> a.getCapturedAt().compareTo(b.getCapturedAt()))
                .collect(Collectors.toList());
    }
}
