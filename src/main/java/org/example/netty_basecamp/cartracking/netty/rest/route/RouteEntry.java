package org.example.netty_basecamp.cartracking.netty.rest.route;

import io.netty.handler.codec.http.HttpMethod;

import java.util.Map;
import java.util.function.Function;

public class RouteEntry {

    private final String httpMethod;
    private final String path;
    private final String[] pathSegments;
    private final boolean hasPathVariable;
    private final Function<RequestContext, Object> handler;

    public RouteEntry(HttpMethod method, String path, Function<RequestContext, Object> handler) {
        this.httpMethod = method.name();
        this.path = path;
        this.pathSegments = path.split("/");
        this.hasPathVariable = path.contains("{");
        this.handler = handler;
    }

    public String getKey() {
        return httpMethod + " " + path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public boolean hasPathVariable() {
        return hasPathVariable;
    }

    public boolean matches(String requestPath, Map<String, String> params) {
        String[] requestSegments = requestPath.split("/");
        if (requestSegments.length != pathSegments.length) {
            return false;
        }
        for (int i = 0; i < pathSegments.length; i++) {
            String pattern = pathSegments[i];
            String actual = requestSegments[i];
            if (pattern.startsWith("{") && pattern.endsWith("}")) {
                params.put(pattern.substring(1, pattern.length() - 1), actual);
            } else if (!pattern.equals(actual)) {
                return false;
            }
        }
        return true;
    }

    public Object handle(RequestContext ctx) {
        return handler.apply(ctx);
    }
}
