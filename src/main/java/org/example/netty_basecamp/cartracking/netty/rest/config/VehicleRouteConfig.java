package org.example.netty_basecamp.cartracking.netty.rest.config;

import io.netty.handler.codec.http.HttpMethod;
import org.example.netty_basecamp.cartracking.netty.rest.controller.VehicleController;
import org.example.netty_basecamp.cartracking.netty.rest.route.RouteEntry;
import org.example.netty_basecamp.cartracking.vehicle.application.VehicleApplicationService;
import org.example.netty_basecamp.cartracking.vehicle.domain.repository.VehicleRepository;
import org.example.netty_basecamp.basic.common.service.impl.CurrentTimeGenerator;

import java.util.List;

public class VehicleRouteConfig {

    public static List<RouteEntry> routes(VehicleRepository vehicleRepository) {
        CurrentTimeGenerator timeGenerator = new CurrentTimeGenerator();
        VehicleApplicationService vehicleService = new VehicleApplicationService(vehicleRepository, timeGenerator);
        VehicleController controller = new VehicleController(vehicleService);

        return List.of(
                new RouteEntry(HttpMethod.POST,   "/api/cartracking/vehicles",      controller::register),
                new RouteEntry(HttpMethod.GET,    "/api/cartracking/vehicles",      controller::findAll),
                new RouteEntry(HttpMethod.GET,    "/api/cartracking/vehicles/{id}", controller::findById)
        );
    }
}
