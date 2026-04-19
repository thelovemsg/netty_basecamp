package org.example.netty_basecamp.domains.vehicle.infrastructure.inmemory;

import org.example.netty_basecamp.domains.vehicle.domain.Trip;
import org.example.netty_basecamp.domains.vehicle.domain.repository.TripRepository;
import org.example.netty_basecamp.domains.vehicle.domain.enums.TripStatusEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryTripRepository implements TripRepository {

    private final Map<Long, Trip> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Trip findById(Long id) {
        return store.get(id);
    }

    @Override
    public Trip findScheduledByVehicleId(Long vehicleId) {
        return store.values().stream()
                .filter(trip -> trip.getVehicleId().equals(vehicleId))
                .filter(trip -> trip.getStatus() == TripStatusEnum.SCHEDULED)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Trip findActiveByVehicleId(Long vehicleId) {
        return store.values().stream()
                .filter(trip -> trip.getVehicleId().equals(vehicleId))
                .filter(trip -> trip.getStatus() == TripStatusEnum.IN_PROGRESS)
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Trip> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Trip save(Trip trip) {
        if (trip.getId() == null) {
            Long newId = sequence.getAndIncrement();
            Trip withId = Trip.builder()
                    .id(newId)
                    .vehicleId(trip.getVehicleId())
                    .origin(trip.getOrigin())
                    .destination(trip.getDestination())
                    .status(trip.getStatus())
                    .startedAt(trip.getStartedAt())
                    .arrivedAt(trip.getArrivedAt())
                    .createdAt(trip.getCreatedAt())
                    .modifiedAt(trip.getModifiedAt())
                    .build();
            store.put(newId, withId);
            return withId;
        }
        store.put(trip.getId(), trip);
        return trip;
    }
}
