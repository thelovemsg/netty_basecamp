package org.example.netty_basecamp.cartracking.vehicle.infrastructure.inmemory;

import org.example.netty_basecamp.cartracking.vehicle.domain.Vehicle;
import org.example.netty_basecamp.cartracking.vehicle.domain.repository.VehicleRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryVehicleRepository implements VehicleRepository {

    private final Map<Long, Vehicle> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Vehicle findById(Long id) {
        return store.get(id);
    }

    @Override
    public List<Vehicle> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        if (vehicle.getId() == null) {
            Long newId = sequence.getAndIncrement();
            Vehicle withId = Vehicle.builder()
                    .id(newId)
                    .plateNumber(vehicle.getPlateNumber())
                    .type(vehicle.getType())
                    .status(vehicle.getStatus())
                    .homeLocation(vehicle.getLocation())
                    .createdAt(vehicle.getCreatedAt())
                    .modifiedAt(vehicle.getModifiedAt())
                    .build();
            store.put(newId, withId);
            return withId;
        }
        store.put(vehicle.getId(), vehicle);
        return vehicle;
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}
