package org.example.netty_basecamp.basic.netty.rest.config;

import io.netty.handler.codec.http.HttpMethod;
import org.example.netty_basecamp.basic.common.service.impl.CurrentTimeGenerator;
import org.example.netty_basecamp.basic.member.application.MemberApplicationService;
import org.example.netty_basecamp.basic.member.infrastructure.InMemoryMemberRepository;
import org.example.netty_basecamp.basic.netty.rest.controller.MemberController;
import org.example.netty_basecamp.basic.netty.rest.route.RouteEntry;

import java.util.List;

public class MemberRouteConfig {

    public static List<RouteEntry> routes() {
        // DI 조립
        CurrentTimeGenerator timeGenerator = new CurrentTimeGenerator();
        InMemoryMemberRepository memberRepository = new InMemoryMemberRepository();
        MemberApplicationService memberService = new MemberApplicationService(memberRepository, timeGenerator);
        MemberController controller = new MemberController(memberService);

        // 라우트 등록
        return List.of(
            new RouteEntry(HttpMethod.POST, "/api/members", controller::create),
            new RouteEntry(HttpMethod.GET, "/api/members", controller::findAll),
            new RouteEntry(HttpMethod.GET, "/api/members/{id}", controller::findById),
            new RouteEntry(HttpMethod.PUT, "/api/members/{id}", controller::update),
            new RouteEntry(HttpMethod.DELETE, "/api/members/{id}", controller::delete)
        );
    }
}
