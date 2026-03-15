package org.example.netty_basecamp.netty.rest.config;

import io.netty.handler.codec.http.HttpMethod;
import org.example.netty_basecamp.domain.common.service.impl.CurrentTimeGenerator;
import org.example.netty_basecamp.domain.member.application.MemberApplicationService;
import org.example.netty_basecamp.domain.member.domain.MembersCreate;
import org.example.netty_basecamp.domain.member.domain.MembersUpdate;
import org.example.netty_basecamp.netty.repository.InMemoryMemberRepository;
import org.example.netty_basecamp.netty.rest.route.RouteEntry;

import java.util.List;
import java.util.Map;

public class MemberRouteConfig {

    public static List<RouteEntry> routes() {
        CurrentTimeGenerator timeGenerator = new CurrentTimeGenerator();
        InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
        MemberApplicationService memberService = new MemberApplicationService(memberRepository, timeGenerator);

        return List.of(
            new RouteEntry(HttpMethod.POST, "/api/members",
                ctx -> memberService.create(ctx.readBody(MembersCreate.class))),

            new RouteEntry(HttpMethod.GET, "/api/members",
                ctx -> memberService.findAll()),

            new RouteEntry(HttpMethod.GET, "/api/members/{id}",
                ctx -> memberService.findById(ctx.pathVariableAsLong("id"))),

            new RouteEntry(HttpMethod.PUT, "/api/members/{id}",
                ctx -> memberService.update(
                        ctx.pathVariableAsLong("id"),
                        ctx.readBody(MembersUpdate.class))),

            new RouteEntry(HttpMethod.DELETE, "/api/members/{id}",
                ctx -> {
                    Long id = ctx.pathVariableAsLong("id");
                    memberService.delete(id);
                    return Map.of("message", "Deleted member: " + id);
                })
        );
    }
}
