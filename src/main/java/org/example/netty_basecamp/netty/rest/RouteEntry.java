package org.example.netty_basecamp.netty.rest;

import io.netty.handler.codec.http.HttpMethod;

import java.util.Map;
import java.util.function.BiFunction;

public class RouteEntry {
    private final String httpMethod;
    private final String path;
    private final BiFunction<Map<String, String>, String, Object> handler;
    // params(쿼리스트링), body(JSON) → 결과 객체

    public RouteEntry(HttpMethod method, String path,
                      BiFunction<Map<String, String>, String, Object> handler) {
        this.httpMethod = method.name();
        this.path = path;
        this.handler = handler;
    }

    public String getKey() {
        return httpMethod + " " + path;
    }

    public Object handle(Map<String, String> params, String body) {
        return handler.apply(params, body);
    }
}