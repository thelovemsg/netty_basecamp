package org.example.netty_basecamp.netty.rest;

import org.example.netty_basecamp.domain.member.domain.Members;
import org.example.netty_basecamp.netty.rest.config.MemberRouteConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        RouteEntry entry = registry.find("POST", "/api/members");
        Members created = (Members) entry.handle(Map.of(), body);

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
        RouteEntry entry = registry.find("GET", "/api/members");
        List<Members> members = (List<Members>) entry.handle(Map.of(), "");

        // then
        assertThat(members).hasSize(2);
    }

    @Test
    @DisplayName("GET /api/members/{id}로 단건 조회한다")
    void 단건_조회() {
        // given
        createMember("홍길동", "서울", 30);

        // when
        Map<String, String> params = new HashMap<>();
        RouteEntry entry = registry.find("GET", "/api/members/1", params);
        Members found = (Members) entry.handle(params, "");

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
        Map<String, String> params = new HashMap<>();
        RouteEntry entry = registry.find("PUT", "/api/members/1", params);
        Members updated = (Members) entry.handle(params, updateBody);

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
        Map<String, String> params = new HashMap<>();
        RouteEntry entry = registry.find("DELETE", "/api/members/1", params);
        entry.handle(params, "");

        // then — 삭제 후 조회 시 예외
        Map<String, String> getParams = new HashMap<>();
        RouteEntry getEntry = registry.find("GET", "/api/members/1", getParams);
        assertThatThrownBy(() -> getEntry.handle(getParams, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("존재하지 않는 회원 조회 시 IllegalArgumentException이 발생한다")
    void 존재하지_않는_회원_조회() {
        Map<String, String> params = new HashMap<>();
        RouteEntry entry = registry.find("GET", "/api/members/999", params);

        assertThatThrownBy(() -> entry.handle(params, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("잘못된 JSON body로 요청하면 IllegalArgumentException이 발생한다")
    void 잘못된_JSON() {
        RouteEntry entry = registry.find("POST", "/api/members");

        assertThatThrownBy(() -> entry.handle(Map.of(), "invalid json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON");
    }

    private void createMember(String name, String address, int age) {
        String body = String.format("""
                {"name":"%s","address":"%s","age":%d}""", name, address, age);
        RouteEntry entry = registry.find("POST", "/api/members");
        entry.handle(Map.of(), body);
    }
}
