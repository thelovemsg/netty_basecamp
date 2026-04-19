package org.example.netty_basecamp.domains.vehicle.application;

import org.example.netty_basecamp.domains.common.service.TimeGenerator;
import org.example.netty_basecamp.domains.vehicle.domain.vo.Location;
import org.example.netty_basecamp.domains.vehicle.domain.LocationSnapshot;
import org.example.netty_basecamp.domains.vehicle.domain.repository.LocationSnapshotRepository;
import org.example.netty_basecamp.domains.vehicle.domain.Trip;
import org.example.netty_basecamp.domains.vehicle.domain.repository.TripRepository;
import org.example.netty_basecamp.domains.vehicle.domain.Vehicle;
import org.example.netty_basecamp.domains.vehicle.domain.repository.VehicleRepository;

import java.util.List;

public class TripApplicationService {

    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final LocationSnapshotRepository locationSnapshotRepository;
    private final TimeGenerator timeGenerator;

    public TripApplicationService(TripRepository tripRepository,
                                  VehicleRepository vehicleRepository,
                                  LocationSnapshotRepository locationSnapshotRepository,
                                  TimeGenerator timeGenerator) {
        this.tripRepository = tripRepository;
        this.vehicleRepository = vehicleRepository;
        this.locationSnapshotRepository = locationSnapshotRepository;
        this.timeGenerator = timeGenerator;
    }

    public Trip scheduleTrip(Long vehicleId, Location origin, Location destination) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId);
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle not found: " + vehicleId);
        }

        long currentTime = timeGenerator.millis();
        Trip trip = Trip.create(vehicleId, origin, destination, currentTime);
        return tripRepository.save(trip);
    }

    public Trip departTrip(Long vehicleId) {
        Trip scheduled = tripRepository.findScheduledByVehicleId(vehicleId);
        if (scheduled == null) {
            throw new IllegalStateException("배차된 운행이 없습니다: vehicleId=" + vehicleId);
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId);
        long currentTime = timeGenerator.millis();

        Trip departed = scheduled.depart(currentTime);
        tripRepository.save(departed);

        Vehicle onTrip = vehicle.startTrip(currentTime);
        vehicleRepository.save(onTrip);

        return departed;
    }

    public LocationSnapshot recordSnapshot(Long vehicleId, Location location) {
        Trip activeTrip = tripRepository.findActiveByVehicleId(vehicleId);
        if (activeTrip == null) {
            throw new IllegalStateException("진행 중인 운행이 없습니다: vehicleId=" + vehicleId);
        }

        long currentTime = timeGenerator.millis();
        LocationSnapshot snapshot = LocationSnapshot.capture(
                activeTrip.getId(), vehicleId, location, currentTime);
        return locationSnapshotRepository.save(snapshot);
    }

    public Trip completeTrip(Long vehicleId) {
        Trip activeTrip = tripRepository.findActiveByVehicleId(vehicleId);
        if (activeTrip == null) {
            throw new IllegalStateException("진행 중인 운행이 없습니다: vehicleId=" + vehicleId);
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId);
        long currentTime = timeGenerator.millis();

        Trip completed = activeTrip.arrive(currentTime);
        tripRepository.save(completed);

        Vehicle returned = vehicle.returnHome(currentTime);
        vehicleRepository.save(returned);

        return completed;
    }

    public List<LocationSnapshot> getTripRoute(Long tripId) {
        return locationSnapshotRepository.findAllByTripId(tripId);
    }
}
