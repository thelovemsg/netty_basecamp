package org.example.netty_basecamp.cartracking.netty.rest.controller;

import org.example.netty_basecamp.cartracking.netty.rest.route.RequestContext;
import org.example.netty_basecamp.cartracking.simulator.SimulatorBootstrap;

import java.util.Map;

public class SimulatorController {

    private final SimulatorBootstrap simulatorBootstrap;

    public SimulatorController(SimulatorBootstrap simulatorBootstrap) {
        this.simulatorBootstrap = simulatorBootstrap;
    }

    public Object start(RequestContext ctx) {
        String countParam = ctx.queryParam("count");
        int vehicleCount = countParam != null ? Integer.parseInt(countParam) : 0;
        simulatorBootstrap.start(vehicleCount);
        return Map.of("message", "시뮬레이터가 시작되었습니다.", "vehicleCount", vehicleCount > 0 ? vehicleCount : "all");
    }

    public Object stop(RequestContext ctx) {
        simulatorBootstrap.stop();
        return Map.of("message", "시뮬레이터가 종료되었습니다.");
    }

    public Object status(RequestContext ctx) {
        return Map.of("running", simulatorBootstrap.isStarted());
    }
}
