package org.example.netty_basecamp.basic.netty.rest.controller;

import org.example.netty_basecamp.basic.fare.application.FarePolicyApplicationService;
import org.example.netty_basecamp.basic.fare.domain.policy.CalculationBasisEnum;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicyCreate;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicyTypeEnum;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicyUpdate;
import org.example.netty_basecamp.basic.netty.rest.route.RequestContext;

import java.math.BigDecimal;
import java.util.Map;

public class FarePolicyController {

    private final FarePolicyApplicationService policyService;

    public FarePolicyController(FarePolicyApplicationService policyService) {
        this.policyService = policyService;
    }

    public Object getPolicies(RequestContext ctx) {
        return policyService.findByFareId(ctx.pathVariableAsLong("fareId"));
    }

    public Object getPolicy(RequestContext ctx) {
        return policyService.findById(ctx.pathVariableAsLong("fareId"), ctx.pathVariableAsLong("policyId"));
    }

    public Object addPolicy(RequestContext ctx) {
        Long fareId = ctx.pathVariableAsLong("fareId");
        FarePolicyCreateRequest req = ctx.readBody(FarePolicyCreateRequest.class);
        FarePolicyCreate command = new FarePolicyCreate(req.type(), req.value(), req.basis(), req.priority());
        return policyService.add(fareId, command);
    }

    public Object updatePolicy(RequestContext ctx) {
        Long fareId = ctx.pathVariableAsLong("fareId");
        Long policyId = ctx.pathVariableAsLong("policyId");
        FarePolicyUpdateRequest req = ctx.readBody(FarePolicyUpdateRequest.class);
        FarePolicyUpdate command = new FarePolicyUpdate(req.value(), req.basis(), req.priority());
        return policyService.update(fareId, policyId, command);
    }

    public Object deletePolicy(RequestContext ctx) {
        Long fareId = ctx.pathVariableAsLong("fareId");
        Long policyId = ctx.pathVariableAsLong("policyId");
        policyService.delete(fareId, policyId);
        return Map.of("message", "Deleted policy: " + policyId);
    }

    record FarePolicyCreateRequest(FarePolicyTypeEnum type, BigDecimal value,
                                   CalculationBasisEnum basis, int priority) {}

    record FarePolicyUpdateRequest(BigDecimal value, CalculationBasisEnum basis, int priority) {}
}
