package org.example.netty_basecamp.cartracking.simulator;

import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;

import java.util.ArrayList;
import java.util.List;

public class GpsInterpolator {

    private static final int MIN_STEPS = 3;
    private static final int MAX_STEPS = 30;

    public List<Location> interpolate(Location origin, Location destination, int steps) {
        List<Location> waypoints = new ArrayList<>(steps);
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
        double latDiff = origin.getLatitude().doubleValue() - destination.getLatitude().doubleValue();
        double lngDiff = origin.getLongitude().doubleValue() - destination.getLongitude().doubleValue();
        double distanceKm = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111;
        int steps = (int) Math.round(distanceKm);
        return Math.max(MIN_STEPS, Math.min(MAX_STEPS, steps));
    }
}
