package org.example.netty_basecamp.basic.netty.rest.controller;

import org.example.netty_basecamp.basic.common.vo.Money;
import org.example.netty_basecamp.basic.fare.application.FareApplicationService;
import org.example.netty_basecamp.basic.fare.domain.FareCreate;
import org.example.netty_basecamp.basic.netty.rest.route.RequestContext;

import java.math.BigDecimal;
import java.util.Map;

public class FareController {

    private final FareApplicationService fareService;

    public FareController(FareApplicationService fareService) {
        this.fareService = fareService;
    }

    public Object getFare(RequestContext ctx) {
        return fareService.findById(ctx.pathVariableAsLong("id"));
    }

    public Object createFare(RequestContext ctx) {
        FareCreateRequest req = ctx.readBody(FareCreateRequest.class);
        FareCreate command = new FareCreate(req.name(), Money.of(req.basePrice()));
        return fareService.create(command);
    }

    public Object deleteFare(RequestContext ctx) {
        Long id = ctx.pathVariableAsLong("id");
        fareService.delete(id);
        return Map.of("message", "Deleted fare: " + id);
    }

    record FareCreateRequest(String name, BigDecimal basePrice) {}
}
