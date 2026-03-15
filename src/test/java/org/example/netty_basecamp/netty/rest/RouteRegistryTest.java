package org.example.netty_basecamp.netty.rest;

import io.netty.handler.codec.http.HttpMethod;
import org.example.netty_basecamp.netty.rest.route.RequestContext;
import org.example.netty_basecamp.netty.rest.route.RouteEntry;
import org.example.netty_basecamp.netty.rest.route.RouteMatch;
import org.example.netty_basecamp.netty.rest.route.RouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteRegistryTest {

    private RouteRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RouteRegistry();
        registry.add(new RouteEntry(HttpMethod.GET, "/api/members",
                ctx -> "findAll"));
        registry.add(new RouteEntry(HttpMethod.GET, "/api/members/{id}",
                ctx -> "findById:" + ctx.pathVariable("id")));
        registry.add(new RouteEntry(HttpMethod.POST, "/api/members",
                ctx -> "create"));
        registry.add(new RouteEntry(HttpMethod.DELETE, "/api/members/{id}",
                ctx -> "delete:" + ctx.pathVariable("id")));
    }

    @Test
    @DisplayName("정확한 경로 매칭으로 라우트를 찾는다")
    void 정확_매칭() {
        RouteMatch match = registry.find("GET", "/api/members");

        assertThat(match).isNotNull();
        RequestContext ctx = requestContext(match);
        assertThat(match.getEntry().handle(ctx)).isEqualTo("findAll");
    }

    @Test
    @DisplayName("path variable이 포함된 경로를 매칭하고 값을 추출한다")
    void pathVariable_매칭() {
        RouteMatch match = registry.find("GET", "/api/members/42");

        assertThat(match).isNotNull();
        assertThat(match.getPathVariables().get("id")).isEqualTo("42");
        RequestContext ctx = requestContext(match);
        assertThat(match.getEntry().handle(ctx)).isEqualTo("findById:42");
    }

    @Test
    @DisplayName("HTTP 메서드가 다르면 매칭되지 않는다")
    void 메서드_불일치() {
        RouteMatch match = registry.find("PUT", "/api/members");

        assertThat(match).isNull();
    }

    @Test
    @DisplayName("등록되지 않은 경로는 null을 반환한다")
    void 미등록_경로() {
        RouteMatch match = registry.find("GET", "/api/unknown");

        assertThat(match).isNull();
    }

    @Test
    @DisplayName("세그먼트 수가 다르면 path variable 매칭에 실패한다")
    void 세그먼트_수_불일치() {
        RouteMatch match = registry.find("GET", "/api/members/1/extra");

        assertThat(match).isNull();
    }

    @Test
    @DisplayName("같은 경로 패턴이라도 HTTP 메서드별로 다른 핸들러가 호출된다")
    void 같은_경로_다른_메서드() {
        RouteMatch getMatch = registry.find("GET", "/api/members/7");
        RouteMatch deleteMatch = registry.find("DELETE", "/api/members/7");

        assertThat(getMatch.getEntry().handle(requestContext(getMatch))).isEqualTo("findById:7");
        assertThat(deleteMatch.getEntry().handle(requestContext(deleteMatch))).isEqualTo("delete:7");
    }

    @Test
    @DisplayName("정확 매칭이 path variable 매칭보다 우선한다")
    void 정확_매칭_우선() {
        RouteMatch match = registry.find("GET", "/api/members");

        assertThat(match).isNotNull();
        RequestContext ctx = requestContext(match);
        assertThat(match.getEntry().handle(ctx)).isEqualTo("findAll");
    }

    private RequestContext requestContext(RouteMatch match) {
        return RequestContext.builder()
                .pathVariables(match.getPathVariables())
                .build();
    }
}
