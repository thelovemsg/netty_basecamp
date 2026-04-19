package org.example.netty_basecamp.cartracking.tracking.infrastructure.inmemory;

import org.example.netty_basecamp.cartracking.tracking.domain.LocationSnapshot;
import org.example.netty_basecamp.cartracking.tracking.domain.repository.LocationSnapshotRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryLocationSnapshotRepository implements LocationSnapshotRepository {

    private final Map<Long, LocationSnapshot> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public LocationSnapshot save(LocationSnapshot snapshot) {
        if (snapshot.getId() == null) {
            Long newId = sequence.getAndIncrement();
            LocationSnapshot withId = LocationSnapshot.builder()
                    .id(newId)
                    .journeyId(snapshot.getJourneyId())
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
    public List<LocationSnapshot> findAllByJourneyId(Long journeyId) {
        return store.values().stream()
                .filter(s -> s.getJourneyId().equals(journeyId))
                .sorted((a, b) -> a.getCapturedAt().compareTo(b.getCapturedAt()))
                .collect(Collectors.toList());
    }
}
