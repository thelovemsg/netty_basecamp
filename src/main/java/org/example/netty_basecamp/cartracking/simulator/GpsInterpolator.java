package org.example.netty_basecamp.cartracking.simulator;

import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;

import java.util.ArrayList;
import java.util.List;

public class GpsInterpolator {

    private static final double MAX_STEP_KM = 0.5;
    private static final int MIN_STEPS = 3;

    public List<Location> interpolate(Location origin, Location destination, int steps) {
        List<Location> waypoints = new ArrayList<>(steps + 1);
        double startLat = origin.getLatitude().doubleValue();
        double startLng = origin.getLongitude().doubleValue();
        double endLat = destination.getLatitude().doubleValue();
        double endLng = destination.getLongitude().doubleValue();

        for (int i = 0; i <= steps; i++) {
            double ratio = (double) i / steps;
            double lat = startLat + (endLat - startLat) * ratio;
            double lng = startLng + (endLng - startLng) * ratio;
            waypoints.add(Location.of(lat, lng));
        }
        return waypoints;
    }

    public int calculateSteps(Location origin, Location destination) {
        double distanceKm = distanceKm(origin, destination);
        int steps = (int) Math.ceil(distanceKm / MAX_STEP_KM);
        return Math.max(MIN_STEPS, steps);
    }

    private double distanceKm(Location a, Location b) {
        double latDiff = a.getLatitude().doubleValue() - b.getLatitude().doubleValue();
        double lngDiff = a.getLongitude().doubleValue() - b.getLongitude().doubleValue();
        return Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111;
    }
}
