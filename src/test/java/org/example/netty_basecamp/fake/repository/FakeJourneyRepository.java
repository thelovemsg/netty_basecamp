package org.example.netty_basecamp.fake.repository;

import org.example.netty_basecamp.cartracking.tracking.domain.Journey;
import org.example.netty_basecamp.cartracking.tracking.domain.enums.JourneyStatusEnum;
import org.example.netty_basecamp.cartracking.tracking.domain.repository.JourneyRepository;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.TrackingTarget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeJourneyRepository implements JourneyRepository {

    private final Map<Long, Journey> store = new HashMap<>();
    private long sequence = 1;

    @Override
    public Journey findById(Long id) {
        return store.get(id);
    }

    @Override
    public Journey findScheduledByTarget(TrackingTarget target) {
        return store.values().stream()
                .filter(j -> j.getTarget().equals(target))
                .filter(j -> j.getStatus() == JourneyStatusEnum.SCHEDULED)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Journey findActiveByTarget(TrackingTarget target) {
        return store.values().stream()
                .filter(j -> j.getTarget().equals(target))
                .filter(j -> j.getStatus() == JourneyStatusEnum.IN_PROGRESS)
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Journey> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Journey save(Journey journey) {
        if (journey.getId() == null) {
            Long newId = sequence++;
            Journey withId = Journey.builder()
                    .id(newId)
                    .target(journey.getTarget())
                    .origin(journey.getOrigin())
                    .destination(journey.getDestination())
                    .status(journey.getStatus())
                    .startedAt(journey.getStartedAt())
                    .arrivedAt(journey.getArrivedAt())
                    .createdAt(journey.getCreatedAt())
                    .modifiedAt(journey.getModifiedAt())
                    .build();
            store.put(newId, withId);
            return withId;
        }
        store.put(journey.getId(), journey);
        return journey;
    }
}
