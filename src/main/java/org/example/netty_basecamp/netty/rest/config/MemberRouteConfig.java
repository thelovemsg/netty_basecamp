package org.example.netty_basecamp.netty.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpMethod;
import org.example.netty_basecamp.domain.member.application.MemberApplicationService;
import org.example.netty_basecamp.domain.member.domain.MembersCreate;
import org.example.netty_basecamp.domain.member.domain.MembersUpdate;
import org.example.netty_basecamp.netty.repository.InMemoryMemberRepository;
import org.example.netty_basecamp.netty.rest.RouteEntry;

import java.util.List;
import java.util.Map;

public class MemberRouteConfig {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<RouteEntry> routes() {
        InMemoryMemberRepository memberRepo = new InMemoryMemberRepository();
        MemberApplicationService memberService = new MemberApplicationService(
                memberRepo, System::currentTimeMillis);

        return List.of(
            new RouteEntry(HttpMethod.POST, "/api/members",
                (params, body) -> memberService.create(readJson(body, MembersCreate.class))),

            new RouteEntry(HttpMethod.GET, "/api/members",
                (params, body) -> memberService.findAll()),

            new RouteEntry(HttpMethod.GET, "/api/members/{id}",
                (params, body) -> memberService.findById(Long.parseLong(params.get("id")))),

            new RouteEntry(HttpMethod.PUT, "/api/members/{id}",
                (params, body) -> memberService.update(
                        Long.parseLong(params.get("id")),
                        readJson(body, MembersUpdate.class))),

            new RouteEntry(HttpMethod.DELETE, "/api/members/{id}",
                (params, body) -> {
                    Long id = Long.parseLong(params.get("id"));
                    memberService.delete(id);
                    return Map.of("message", "Deleted member: " + id);
                })
        );
    }

    private static <T> T readJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage());
        }
    }
}
