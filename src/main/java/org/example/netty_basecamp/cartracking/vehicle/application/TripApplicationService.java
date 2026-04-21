package org.example.netty_basecamp.cartracking.vehicle.application;

import org.example.netty_basecamp.basic.common.service.TimeGenerator;
import org.example.netty_basecamp.cartracking.tracking.domain.Journey;
import org.example.netty_basecamp.cartracking.tracking.domain.LocationSnapshot;
import org.example.netty_basecamp.cartracking.tracking.domain.enums.TrackingTargetTypeEnum;
import org.example.netty_basecamp.cartracking.tracking.domain.repository.JourneyRepository;
import org.example.netty_basecamp.cartracking.tracking.domain.repository.LocationSnapshotRepository;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.TrackingTarget;
import org.example.netty_basecamp.cartracking.vehicle.domain.Vehicle;
import org.example.netty_basecamp.cartracking.vehicle.domain.repository.VehicleRepository;

import java.util.List;

public class TripApplicationService {

    private final JourneyRepository journeyRepository;
    private final VehicleRepository vehicleRepository;
    private final LocationSnapshotRepository locationSnapshotRepository;
    private final TimeGenerator timeGenerator;

    public TripApplicationService(JourneyRepository journeyRepository,
                                  VehicleRepository vehicleRepository,
                                  LocationSnapshotRepository locationSnapshotRepository,
                                  TimeGenerator timeGenerator) {
        this.journeyRepository = journeyRepository;
        this.vehicleRepository = vehicleRepository;
        this.locationSnapshotRepository = locationSnapshotRepository;
        this.timeGenerator = timeGenerator;
    }

    public Journey startTrip(Long vehicleId, Location origin) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId);
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle not found: " + vehicleId);
        }

        long currentTime = timeGenerator.millis();
        TrackingTarget target = TrackingTarget.of(vehicleId, TrackingTargetTypeEnum.VEHICLE);
        Journey journey = Journey.create(target, origin, null, currentTime);
        Journey departed = journey.depart(currentTime);
        journeyRepository.save(departed);

        vehicleRepository.save(vehicle.startTrip(currentTime));
        return departed;
    }

    public LocationSnapshot recordSnapshot(Long vehicleId, Location location) {
        TrackingTarget target = TrackingTarget.of(vehicleId, TrackingTargetTypeEnum.VEHICLE);
        Journey activeJourney = journeyRepository.findActiveByTarget(target);
        if (activeJourney == null) {
            return null;
        }

        long currentTime = timeGenerator.millis();
        LocationSnapshot snapshot = LocationSnapshot.capture(activeJourney.getId(), location, currentTime);
        return locationSnapshotRepository.save(snapshot);
    }

    public Journey completeTrip(Long vehicleId) {
        TrackingTarget target = TrackingTarget.of(vehicleId, TrackingTargetTypeEnum.VEHICLE);
        Journey activeJourney = journeyRepository.findActiveByTarget(target);
        if (activeJourney == null) {
            throw new IllegalStateException("진행 중인 운행이 없습니다: vehicleId=" + vehicleId);
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId);
        long currentTime = timeGenerator.millis();

        journeyRepository.save(activeJourney.arrive(currentTime));
        vehicleRepository.save(vehicle.returnHome(currentTime));
        return activeJourney.arrive(currentTime);
    }

    public List<Journey> getVehicleJourneys(Long vehicleId) {
        TrackingTarget target = TrackingTarget.of(vehicleId, TrackingTargetTypeEnum.VEHICLE);
        return journeyRepository.findAllByTarget(target);
    }

    public List<LocationSnapshot> getTripRoute(Long journeyId) {
        return locationSnapshotRepository.findAllByJourneyId(journeyId);
    }
}
