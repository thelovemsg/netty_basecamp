package org.example.netty_basecamp.cartracking.vehicle.application;

import org.example.netty_basecamp.domains.common.service.TimeGenerator;
import org.example.netty_basecamp.cartracking.vehicle.domain.Vehicle;
import org.example.netty_basecamp.cartracking.vehicle.domain.dto.VehicleCreate;
import org.example.netty_basecamp.cartracking.vehicle.domain.repository.VehicleRepository;

import java.util.List;

public class VehicleApplicationService {

    private final VehicleRepository vehicleRepository;
    private final TimeGenerator timeGenerator;

    public VehicleApplicationService(VehicleRepository vehicleRepository, TimeGenerator timeGenerator) {
        this.vehicleRepository = vehicleRepository;
        this.timeGenerator = timeGenerator;
    }

    public Vehicle register(VehicleCreate create) {
        Vehicle vehicle = Vehicle.create(create, timeGenerator.millis());
        return vehicleRepository.save(vehicle);
    }

    public Vehicle findById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id);
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle not found: " + id);
        }
        return vehicle;
    }

    public List<Vehicle> findAll() {
        return vehicleRepository.findAll();
    }
}
