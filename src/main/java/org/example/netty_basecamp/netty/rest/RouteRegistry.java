package org.example.netty_basecamp.netty.rest;

import java.util.HashMap;
import java.util.Map;

public class RouteRegistry {
    private final Map<String, RouteEntry> routes = new HashMap<>();

    public RouteRegistry add(RouteEntry entry) {
        routes.put(entry.getKey(), entry);
        return this;
    }

    public RouteEntry find(String method, String path) {
        return routes.get(method + " " + path);
    }
}