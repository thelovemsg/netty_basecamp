package org.example.netty_basecamp.netty.rest.filter;

import org.example.netty_basecamp.netty.rest.route.RequestContext;

/**
 * 인증 필터 스켈레톤.
 * 현재는 Authorization 헤더 존재 여부만 검사한다.
 * 나중에 JWT 검증, 토큰 만료 체크 등으로 확장 가능.
 */
public class AuthFilter implements RouteFilter {

    @Override
    public RequestContext filter(RequestContext ctx) {
        String authorization = ctx.header("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new UnauthorizedException("Authorization header is missing");
        }
        // TODO: JWT 파싱, 토큰 검증, 만료 체크 등
        return ctx;
    }
}
