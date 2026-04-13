package org.example.netty_basecamp.netty.rest;

import org.example.netty_basecamp.netty.rest.config.FareRouteConfig;
import org.example.netty_basecamp.netty.rest.config.MemberRouteConfig;
import org.example.netty_basecamp.netty.rest.route.RouteRegistry;

public class AppConfig {

    public static RouteRegistry initRoutes() {
        RouteRegistry registry = new RouteRegistry();

        MemberRouteConfig.routes().forEach(registry::add);
        FareRouteConfig.routes().forEach(registry::add);
        // CouponRouteConfig.routes().forEach(registry::add);

        return registry;
    }
}
