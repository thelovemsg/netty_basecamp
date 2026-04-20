package org.example.netty_basecamp.cartracking.netty.rest.controller;

import org.example.netty_basecamp.cartracking.netty.rest.dto.VehicleRegisterRequest;
import org.example.netty_basecamp.cartracking.netty.rest.route.RequestContext;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.example.netty_basecamp.cartracking.vehicle.application.VehicleApplicationService;
import org.example.netty_basecamp.cartracking.vehicle.domain.dto.VehicleCreate;

public class VehicleController {

    private final VehicleApplicationService vehicleService;

    public VehicleController(VehicleApplicationService vehicleService) {
        this.vehicleService = vehicleService;
    }

    public Object register(RequestContext ctx) {
        VehicleRegisterRequest req = ctx.readBody(VehicleRegisterRequest.class);
        VehicleCreate create = VehicleCreate.builder()
                .plateNumber(req.plateNumber())
                .type(req.type())
                .homeLocation(Location.of(req.homeLat(), req.homeLng()))
                .build();
        return vehicleService.register(create);
    }

    public Object findAll(RequestContext ctx) {
        return vehicleService.findAll();
    }

    public Object findById(RequestContext ctx) {
        return vehicleService.findById(ctx.pathVariableAsLong("id"));
    }
}
