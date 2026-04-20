package org.example.netty_basecamp.cartracking.simulator;

import org.example.netty_basecamp.cartracking.mqtt.TelemetryPayload;
import org.example.netty_basecamp.cartracking.mqtt.TelemetryPublisher;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class VehicleSimulator implements Runnable {

    private static final Logger log = LogManager.getLogger(VehicleSimulator.class);

    private final Long vehicleId;
    private final SeoulRouteGenerator seoulRouteGenerator;
    private final GpsInterpolator interpolator;
    private final TelemetryPublisher publisher;
    private final long intervalMillis;
    private volatile boolean running = true;

    public VehicleSimulator(Long vehicleId, SeoulRouteGenerator seoulRouteGenerator,
                            GpsInterpolator interpolator, TelemetryPublisher publisher,
                            long intervalMillis) {
        this.vehicleId = vehicleId;
        this.seoulRouteGenerator = seoulRouteGenerator;
        this.interpolator = interpolator;
        this.publisher = publisher;
        this.intervalMillis = intervalMillis;
    }

    @Override
    public void run() {
        log.info("차량 시뮬레이터 시작 [vehicleId={}]", vehicleId);
        while (running) {
            try {
                Location[] route = seoulRouteGenerator.randomRoute();
                int steps = interpolator.calculateSteps(route[0], route[1]);
                List<Location> waypoints = interpolator.interpolate(route[0], route[1], steps);
                double speed = calculateSpeed(steps);

                for (Location loc : waypoints) {
                    if (!running) break;
                    TelemetryPayload payload = new TelemetryPayload(
                            vehicleId, loc.getLatitude(), loc.getLongitude(),
                            speed, "ON_TRIP", System.currentTimeMillis()
                    );
                    publisher.publish(payload);
                    Thread.sleep(intervalMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("시뮬레이터 오류 [vehicleId={}]: {}", vehicleId, e.getMessage());
            }
        }
        log.info("차량 시뮬레이터 종료 [vehicleId={}]", vehicleId);
    }

    public void stop() {
        running = false;
    }

    private double calculateSpeed(int steps) {
        return 30.0 + (steps * 2.0);
    }
}
