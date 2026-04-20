package org.example.netty_basecamp.cartracking.netty.rest;

import org.example.netty_basecamp.cartracking.netty.rest.config.TripRouteConfig;
import org.example.netty_basecamp.cartracking.netty.rest.config.VehicleRouteConfig;
import org.example.netty_basecamp.cartracking.netty.rest.route.RouteRegistry;
import org.example.netty_basecamp.cartracking.vehicle.domain.repository.VehicleRepository;
import org.example.netty_basecamp.cartracking.vehicle.infrastructure.inmemory.InMemoryVehicleRepository;

public class CarTrackingAppConfig {

    public static RouteRegistry initRoutes() {
        // VehicleRepository를 공유 — Vehicle 상태가 Trip과 동기화되어야 하므로 동일 인스턴스
        VehicleRepository vehicleRepository = new InMemoryVehicleRepository();

        RouteRegistry registry = new RouteRegistry();
        VehicleRouteConfig.routes(vehicleRepository).forEach(registry::add);
        TripRouteConfig.routes(vehicleRepository).forEach(registry::add);
        return registry;
    }
}
