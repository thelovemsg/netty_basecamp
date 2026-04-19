package org.example.netty_basecamp.cartracking.vehicle.domain.repository;

import org.example.netty_basecamp.cartracking.vehicle.domain.Vehicle;

import java.util.List;

public interface VehicleRepository {
    Vehicle findById(Long id);
    List<Vehicle> findAll();
    Vehicle save(Vehicle vehicle);
    void deleteById(Long id);
}
