package org.example.netty_basecamp.cartracking;

import org.example.netty_basecamp.cartracking.netty.CarTrackingServer;
import org.example.netty_basecamp.cartracking.netty.rest.CarTrackingAppConfig;

public class CarTrackingApplication {
    public static void main(String[] args) throws Exception {
        CarTrackingAppConfig.BootstrapResult result = CarTrackingAppConfig.init();
        new CarTrackingServer(8081, result.routeRegistry(), result.websocketClients()).start();
    }
}
