package org.example.netty_basecamp.cartracking.simulator;

import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;

import java.util.Random;

public class SeoulRouteGenerator {

    private static final double LATITUDE_MIN = 37.42;
    private static final double LATITUDE_MAX = 37.70;
    private static final double LONGITUDE_MIN = 126.80;
    private static final double LONGITUDE_MAX = 127.18;

    private final Random random = new Random();

    public Location randomLocation() {
        double lat = LATITUDE_MIN + random.nextDouble() * (LATITUDE_MAX - LATITUDE_MIN);
        double lng = LONGITUDE_MIN + random.nextDouble() * (LONGITUDE_MAX - LONGITUDE_MIN);
        return Location.of(lat, lng);
    }

    public Location[] randomRoute() {
        Location origin = randomLocation();
        Location destination;
        do {
            destination = randomLocation();
        } while (origin.equals(destination));
        return new Location[]{origin, destination};
    }
}
