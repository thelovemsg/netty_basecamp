package org.example.netty_basecamp.cartracking;

import org.example.netty_basecamp.cartracking.netty.CarTrackingServer;
import org.example.netty_basecamp.cartracking.netty.rest.CarTrackingAppConfig;
import org.example.netty_basecamp.cartracking.netty.rest.route.RouteRegistry;

public class CarTrackingApplication {
    public static void main(String[] args) throws Exception {
        RouteRegistry registry = CarTrackingAppConfig.initRoutes();
        new CarTrackingServer(8081, registry).start();
    }
}
