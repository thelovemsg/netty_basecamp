package org.example.netty_basecamp.netty.rest.filter;

import org.example.netty_basecamp.netty.rest.route.RequestContext;

/**
 * 핸들러 실행 전에 RequestContext를 검사하는 필터.
 * 통과하면 그대로 반환, 차단하면 예외를 던진다.
 */
@FunctionalInterface
public interface RouteFilter {

    RequestContext filter(RequestContext ctx);
}
