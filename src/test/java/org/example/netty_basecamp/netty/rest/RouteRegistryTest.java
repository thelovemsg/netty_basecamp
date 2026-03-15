package org.example.netty_basecamp.netty.rest;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteRegistryTest {

    private RouteRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RouteRegistry();
        registry.add(new RouteEntry(HttpMethod.GET, "/api/members",
                (params, body) -> "findAll"));
        registry.add(new RouteEntry(HttpMethod.GET, "/api/members/{id}",
                (params, body) -> "findById:" + params.get("id")));
        registry.add(new RouteEntry(HttpMethod.POST, "/api/members",
                (params, body) -> "create"));
        registry.add(new RouteEntry(HttpMethod.DELETE, "/api/members/{id}",
                (params, body) -> "delete:" + params.get("id")));
    }

    @Test
    @DisplayName("정확한 경로 매칭으로 라우트를 찾는다")
    void 정확_매칭() {
        RouteEntry entry = registry.find("GET", "/api/members");

        assertThat(entry).isNotNull();
        assertThat(entry.handle(Map.of(), "")).isEqualTo("findAll");
    }

    @Test
    @DisplayName("path variable이 포함된 경로를 매칭하고 값을 추출한다")
    void pathVariable_매칭() {
        Map<String, String> pathParams = new HashMap<>();
        RouteEntry entry = registry.find("GET", "/api/members/42", pathParams);

        assertThat(entry).isNotNull();
        assertThat(pathParams.get("id")).isEqualTo("42");
        assertThat(entry.handle(pathParams, "")).isEqualTo("findById:42");
    }

    @Test
    @DisplayName("HTTP 메서드가 다르면 매칭되지 않는다")
    void 메서드_불일치() {
        RouteEntry entry = registry.find("PUT", "/api/members");

        assertThat(entry).isNull();
    }

    @Test
    @DisplayName("등록되지 않은 경로는 null을 반환한다")
    void 미등록_경로() {
        RouteEntry entry = registry.find("GET", "/api/unknown");

        assertThat(entry).isNull();
    }

    @Test
    @DisplayName("세그먼트 수가 다르면 path variable 매칭에 실패한다")
    void 세그먼트_수_불일치() {
        RouteEntry entry = registry.find("GET", "/api/members/1/extra");

        assertThat(entry).isNull();
    }

    @Test
    @DisplayName("같은 경로 패턴이라도 HTTP 메서드별로 다른 핸들러가 호출된다")
    void 같은_경로_다른_메서드() {
        Map<String, String> getParams = new HashMap<>();
        Map<String, String> deleteParams = new HashMap<>();

        RouteEntry getEntry = registry.find("GET", "/api/members/7", getParams);
        RouteEntry deleteEntry = registry.find("DELETE", "/api/members/7", deleteParams);

        assertThat(getEntry.handle(getParams, "")).isEqualTo("findById:7");
        assertThat(deleteEntry.handle(deleteParams, "")).isEqualTo("delete:7");
    }

    @Test
    @DisplayName("정확 매칭이 path variable 매칭보다 우선한다")
    void 정확_매칭_우선() {
        // /api/members는 정확 매칭, /api/members/{id}는 패턴 매칭
        // GET /api/members 요청 시 정확 매칭이 선택되어야 한다
        RouteEntry entry = registry.find("GET", "/api/members");

        assertThat(entry).isNotNull();
        assertThat(entry.handle(Map.of(), "")).isEqualTo("findAll");
    }
}
