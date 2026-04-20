package org.example.netty_basecamp;

import org.example.netty_basecamp.basic.netty.NettyBootcampServer;
import org.example.netty_basecamp.basic.netty.rest.AppConfig;
import org.example.netty_basecamp.basic.netty.rest.route.RouteRegistry;

public class NettyBaseCampApplication {
    public static void main(String[] args) throws Exception {
        System.out.println("=== main 시작 ===");
        RouteRegistry registry = AppConfig.initRoutes();
        System.out.println("=== registry 생성 완료 ===");
        new NettyBootcampServer(8080, registry).start();
    }
}
