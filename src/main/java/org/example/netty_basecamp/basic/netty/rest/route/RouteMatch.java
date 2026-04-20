package org.example.netty_basecamp.basic.netty.rest.route;

import java.util.Map;

public class RouteMatch {

    private final RouteEntry entry;
    private final Map<String, String> pathVariables;

    public RouteMatch(RouteEntry entry, Map<String, String> pathVariables) {
        this.entry = entry;
        this.pathVariables = pathVariables;
    }

    public RouteEntry getEntry() {
        return entry;
    }

    public Map<String, String> getPathVariables() {
        return pathVariables;
    }
}
