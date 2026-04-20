package org.example.netty_basecamp.cartracking.netty.rest.config;

import io.netty.handler.codec.http.HttpMethod;
import org.example.netty_basecamp.cartracking.netty.rest.controller.TripController;
import org.example.netty_basecamp.cartracking.netty.rest.route.RouteEntry;
import org.example.netty_basecamp.cartracking.tracking.infrastructure.inmemory.InMemoryJourneyRepository;
import org.example.netty_basecamp.cartracking.tracking.infrastructure.inmemory.InMemoryLocationSnapshotRepository;
import org.example.netty_basecamp.cartracking.vehicle.application.TripApplicationService;
import org.example.netty_basecamp.cartracking.vehicle.domain.repository.VehicleRepository;
import org.example.netty_basecamp.basic.common.service.impl.CurrentTimeGenerator;

import java.util.List;

public class TripRouteConfig {

    public static List<RouteEntry> routes(VehicleRepository vehicleRepository) {
        CurrentTimeGenerator timeGenerator = new CurrentTimeGenerator();
        TripApplicationService tripService = new TripApplicationService(
                new InMemoryJourneyRepository(),
                vehicleRepository,
                new InMemoryLocationSnapshotRepository(),
                timeGenerator);
        TripController controller = new TripController(tripService);

        return List.of(
                new RouteEntry(HttpMethod.POST, "/api/cartracking/trips",                          controller::schedule),
                new RouteEntry(HttpMethod.POST, "/api/cartracking/trips/{vehicleId}/depart",       controller::depart),
                new RouteEntry(HttpMethod.POST, "/api/cartracking/trips/{vehicleId}/snapshots",    controller::snapshot),
                new RouteEntry(HttpMethod.POST, "/api/cartracking/trips/{vehicleId}/complete",     controller::complete),
                new RouteEntry(HttpMethod.GET,  "/api/cartracking/trips/{journeyId}/route",        controller::route)
        );
    }
}
