package org.example.netty_basecamp.cartracking.netty.rest.config;

import io.netty.handler.codec.http.HttpMethod;
import org.example.netty_basecamp.cartracking.netty.rest.controller.JourneyController;
import org.example.netty_basecamp.cartracking.netty.rest.route.RouteEntry;
import org.example.netty_basecamp.cartracking.vehicle.application.TripApplicationService;

import java.util.List;

public class JourneyRouteConfig {

    public static List<RouteEntry> routes(TripApplicationService tripService) {
        JourneyController controller = new JourneyController(tripService);
        return List.of(
                new RouteEntry(HttpMethod.GET, "/api/cartracking/vehicles/{vehicleId}/journeys", controller::listByVehicle),
                new RouteEntry(HttpMethod.GET, "/api/cartracking/journeys/{journeyId}/route",   controller::route)
        );
    }
}
