package org.example.netty_basecamp.cartracking.netty.rest.config;

import io.netty.handler.codec.http.HttpMethod;
import org.example.netty_basecamp.cartracking.netty.rest.controller.SimulatorController;
import org.example.netty_basecamp.cartracking.netty.rest.route.RouteEntry;
import org.example.netty_basecamp.cartracking.simulator.SimulatorBootstrap;

import java.util.List;

public class SimulatorRouteConfig {

    public static List<RouteEntry> routes(SimulatorBootstrap simulatorBootstrap) {
        SimulatorController controller = new SimulatorController(simulatorBootstrap);

        return List.of(
                new RouteEntry(HttpMethod.POST, "/api/cartracking/simulator/start",  controller::start),
                new RouteEntry(HttpMethod.POST, "/api/cartracking/simulator/stop",   controller::stop),
                new RouteEntry(HttpMethod.GET,  "/api/cartracking/simulator/status", controller::status)
        );
    }
}
