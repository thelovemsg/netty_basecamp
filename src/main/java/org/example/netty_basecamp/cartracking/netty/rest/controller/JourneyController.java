package org.example.netty_basecamp.cartracking.netty.rest.controller;

import org.example.netty_basecamp.cartracking.netty.rest.route.RequestContext;
import org.example.netty_basecamp.cartracking.vehicle.application.TripApplicationService;

public class JourneyController {

    private final TripApplicationService tripService;

    public JourneyController(TripApplicationService tripService) {
        this.tripService = tripService;
    }

    public Object listByVehicle(RequestContext ctx) {
        Long vehicleId = ctx.pathVariableAsLong("vehicleId");
        return tripService.getVehicleJourneys(vehicleId);
    }

    public Object route(RequestContext ctx) {
        Long journeyId = ctx.pathVariableAsLong("journeyId");
        return tripService.getTripRoute(journeyId);
    }
}
