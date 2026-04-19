package org.example.netty_basecamp.domains.vehicle.domain.repository;

import org.example.netty_basecamp.domains.vehicle.domain.Trip;

import java.util.List;

public interface TripRepository {
    Trip findById(Long id);
    Trip findScheduledByVehicleId(Long vehicleId);
    Trip findActiveByVehicleId(Long vehicleId);
    List<Trip> findAll();
    Trip save(Trip trip);
}
