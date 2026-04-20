package org.example.netty_basecamp.basic.netty.rest;

import org.example.netty_basecamp.basic.member.domain.Members;
import org.example.netty_basecamp.basic.netty.rest.config.MemberRouteConfig;
import org.example.netty_basecamp.basic.netty.rest.route.RequestContext;
import org.example.netty_basecamp.basic.netty.rest.route.RouteMatch;
import org.example.netty_basecamp.basic.netty.rest.route.RouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberRouteConfigTest {

    private RouteRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RouteRegistry();
        MemberRouteConfig.routes().forEach(registry::add);
    }

    @Test
    @DisplayName("POST /api/members로 회원을 생성한다")
    void 회원_생성() {
        String body = """
                {"name":"홍길동","address":"서울","age":30}""";

        RouteMatch match = registry.find("POST", "/api/members");
        RequestContext ctx = requestContext(match, body);
        Members created = (Members) match.getEntry().handle(ctx);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getName()).isEqualTo("홍길동");
        assertThat(created.getAddress()).isEqualTo("서울");
        assertThat(created.getAge()).isEqualTo(30);
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("GET /api/members로 전체 회원을 조회한다")
    @SuppressWarnings("unchecked")
    void 전체_조회() {
        // given
        createMember("홍길동", "서울", 30);
        createMember("김철수", "부산", 25);

        // when
        RouteMatch match = registry.find("GET", "/api/members");
        RequestContext ctx = requestContext(match, "");
        List<Members> members = (List<Members>) match.getEntry().handle(ctx);

        // then
        assertThat(members).hasSize(2);
    }

    @Test
    @DisplayName("GET /api/members/{id}로 단건 조회한다")
    void 단건_조회() {
        // given
        createMember("홍길동", "서울", 30);

        // when
        RouteMatch match = registry.find("GET", "/api/members/1");
        RequestContext ctx = requestContext(match, "");
        Members found = (Members) match.getEntry().handle(ctx);

        // then
        assertThat(found.getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("PUT /api/members/{id}로 회원 정보를 수정한다")
    void 회원_수정() {
        // given
        createMember("홍길동", "서울", 30);
        String updateBody = """
                {"name":"수정됨","address":"부산","age":31}""";

        // when
        RouteMatch match = registry.find("PUT", "/api/members/1");
        RequestContext ctx = requestContext(match, updateBody);
        Members updated = (Members) match.getEntry().handle(ctx);

        // then
        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getName()).isEqualTo("수정됨");
        assertThat(updated.getAddress()).isEqualTo("부산");
    }

    @Test
    @DisplayName("DELETE /api/members/{id}로 회원을 삭제한다")
    void 회원_삭제() {
        // given
        createMember("홍길동", "서울", 30);

        // when
        RouteMatch match = registry.find("DELETE", "/api/members/1");
        RequestContext ctx = requestContext(match, "");
        match.getEntry().handle(ctx);

        // then — 삭제 후 조회 시 예외
        RouteMatch getMatch = registry.find("GET", "/api/members/1");
        RequestContext getCtx = requestContext(getMatch, "");
        assertThatThrownBy(() -> getMatch.getEntry().handle(getCtx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("존재하지 않는 회원 조회 시 IllegalArgumentException이 발생한다")
    void 존재하지_않는_회원_조회() {
        RouteMatch match = registry.find("GET", "/api/members/999");
        RequestContext ctx = requestContext(match, "");

        assertThatThrownBy(() -> match.getEntry().handle(ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("잘못된 JSON body로 요청하면 IllegalArgumentException이 발생한다")
    void 잘못된_JSON() {
        RouteMatch match = registry.find("POST", "/api/members");
        RequestContext ctx = requestContext(match, "invalid json");

        assertThatThrownBy(() -> match.getEntry().handle(ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON");
    }

    private void createMember(String name, String address, int age) {
        String body = String.format("""
                {"name":"%s","address":"%s","age":%d}""", name, address, age);
        RouteMatch match = registry.find("POST", "/api/members");
        match.getEntry().handle(requestContext(match, body));
    }

    private RequestContext requestContext(RouteMatch match, String body) {
        return RequestContext.builder()
                .pathVariables(match.getPathVariables())
                .body(body)
                .build();
    }
}
