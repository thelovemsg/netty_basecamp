package org.example.netty_basecamp.cartracking.netty.rest.route;

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

    public RouteMatch find(String method, String path) {
        RouteEntry exact = exactRoutes.get(method + " " + path);
        if (exact != null) {
            return new RouteMatch(exact, Map.of());
        }
        for (RouteEntry entry : paramRoutes) {
            if (!entry.getHttpMethod().equals(method)) {
                continue;
            }
            Map<String, String> extracted = new HashMap<>();
            if (entry.matches(path, extracted)) {
                return new RouteMatch(entry, extracted);
            }
        }
        return null;
    }
}
