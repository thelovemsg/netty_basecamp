package org.example.netty_basecamp.netty.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteRegistry {
    private final Map<String, RouteEntry> exactRoutes = new HashMap<>();
    private final List<RouteEntry> paramRoutes = new ArrayList<>();

    public RouteRegistry add(RouteEntry entry) {
        if (entry.hasPathVariable()) {
            paramRoutes.add(entry);
        } else {
            exactRoutes.put(entry.getKey(), entry);
        }
        return this;
    }

    public RouteEntry find(String method, String path) {
        return find(method, path, null);
    }

    public RouteEntry find(String method, String path, Map<String, String> pathParams) {
        // 1. 정확 매칭 우선
        RouteEntry exact = exactRoutes.get(method + " " + path);
        if (exact != null) {
            return exact;
        }

        // 2. Path variable 패턴 매칭
        for (RouteEntry entry : paramRoutes) {
            if (!entry.getHttpMethod().equals(method)) {
                continue;
            }
            Map<String, String> extracted = new HashMap<>();
            if (entry.matches(path, extracted)) {
                if (pathParams != null) {
                    pathParams.putAll(extracted);
                }
                return entry;
            }
        }
        return null;
    }
}
