package org.example.netty_basecamp.basic.netty.rest.config;

import io.netty.handler.codec.http.HttpMethod;
import org.example.netty_basecamp.basic.common.service.impl.CurrentTimeGenerator;
import org.example.netty_basecamp.basic.fare.application.FareApplicationService;
import org.example.netty_basecamp.basic.fare.application.FarePolicyApplicationService;
import org.example.netty_basecamp.basic.fare.infrastructure.InMemoryFarePolicyRepository;
import org.example.netty_basecamp.basic.fare.infrastructure.InMemoryFareRepository;
import org.example.netty_basecamp.basic.netty.rest.controller.FareController;
import org.example.netty_basecamp.basic.netty.rest.controller.FarePolicyController;
import org.example.netty_basecamp.basic.netty.rest.route.RouteEntry;

import java.util.List;

public class FareRouteConfig {

    public static List<RouteEntry> routes() {
        // DI 조립
        InMemoryFareRepository fareRepository = new InMemoryFareRepository();
        InMemoryFarePolicyRepository farePolicyRepository = new InMemoryFarePolicyRepository();
        CurrentTimeGenerator timeGenerator = new CurrentTimeGenerator();
        FareApplicationService fareService = new FareApplicationService(fareRepository, timeGenerator);
        FarePolicyApplicationService policyService = new FarePolicyApplicationService(fareRepository, farePolicyRepository, timeGenerator);
        FareController fareController = new FareController(fareService);
        FarePolicyController policyController = new FarePolicyController(policyService);

        // 라우트 등록
        return List.of(
            new RouteEntry(HttpMethod.GET,    "/api/fares/{id}",                               fareController::getFare),
            new RouteEntry(HttpMethod.POST,   "/api/fares",                                    fareController::createFare),
            new RouteEntry(HttpMethod.DELETE, "/api/fares/{id}",                               fareController::deleteFare),
            new RouteEntry(HttpMethod.GET,    "/api/fares/{fareId}/policies",                  policyController::getPolicies),
            new RouteEntry(HttpMethod.GET,    "/api/fares/{fareId}/policies/{policyId}",       policyController::getPolicy),
            new RouteEntry(HttpMethod.POST,   "/api/fares/{fareId}/policies",                  policyController::addPolicy),
            new RouteEntry(HttpMethod.PUT,    "/api/fares/{fareId}/policies/{policyId}",       policyController::updatePolicy),
            new RouteEntry(HttpMethod.DELETE, "/api/fares/{fareId}/policies/{policyId}",       policyController::deletePolicy)
        );
    }
}
