package org.example.netty_basecamp.netty.rest;

import io.netty.handler.codec.http.HttpMethod;

public class AppConfig {
    public static RouteRegistry initRoutes() {
//        FareService fareService = new FareService(new FareCalculator());
//        CouponService couponService = new CouponService();

        return new RouteRegistry()
            .add(new RouteEntry(HttpMethod.GET, "/api/fare/calculate",
                (params, body) -> {
                    System.out.println("params = " + params);
                    System.out.println("body = " + body);
                    return "test1";
                }))

            .add(new RouteEntry(HttpMethod.POST, "/api/coupon/apply",
                (params, body) -> {
                    System.out.println("params = " + params);
                    System.out.println("body = " + body);
                    return "test2";
                }));
            // 새 API? 여기 한 줄 추가
//        ;
    }
}