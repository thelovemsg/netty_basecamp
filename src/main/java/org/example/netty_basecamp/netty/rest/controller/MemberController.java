package org.example.netty_basecamp.netty.rest.controller;

import org.example.netty_basecamp.domains.member.application.MemberApplicationService;
import org.example.netty_basecamp.domains.member.domain.MembersCreate;
import org.example.netty_basecamp.domains.member.domain.MembersUpdate;
import org.example.netty_basecamp.netty.rest.route.RequestContext;

import java.util.Map;

public class MemberController {

    private final MemberApplicationService memberService;

    public MemberController(MemberApplicationService memberService) {
        this.memberService = memberService;
    }

    public Object create(RequestContext ctx) {
        return memberService.create(ctx.readBody(MembersCreate.class));
    }

    public Object findAll(RequestContext ctx) {
        return memberService.findAll();
    }

    public Object findById(RequestContext ctx) {
        return memberService.findById(ctx.pathVariableAsLong("id"));
    }

    public Object update(RequestContext ctx) {
        return memberService.update(
                ctx.pathVariableAsLong("id"),
                ctx.readBody(MembersUpdate.class));
    }

    public Object delete(RequestContext ctx) {
        // TODO: 인가(Authorization) 체크 — 예시:
        //   if (!ctx.isAuthenticated()) throw new IllegalStateException("Unauthorized");
        //   if (!"ADMIN".equals(ctx.getAuthInfo().getRole())) throw new IllegalStateException("Forbidden");
        Long id = ctx.pathVariableAsLong("id");
        memberService.delete(id);
        return Map.of("message", "Deleted member: " + id);
    }
}
