package org.example.netty_basecamp.cartracking.netty.rest;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import org.example.netty_basecamp.cartracking.mqtt.MqttClientFactory;
import org.example.netty_basecamp.cartracking.mqtt.TelemetryPublisher;
import org.example.netty_basecamp.cartracking.netty.rest.config.SimulatorRouteConfig;
import org.example.netty_basecamp.cartracking.netty.rest.config.TripRouteConfig;
import org.example.netty_basecamp.cartracking.netty.rest.config.VehicleRouteConfig;
import org.example.netty_basecamp.cartracking.netty.rest.route.RouteRegistry;
import org.example.netty_basecamp.cartracking.simulator.SimulatorBootstrap;
import org.example.netty_basecamp.cartracking.vehicle.domain.repository.VehicleRepository;
import org.example.netty_basecamp.cartracking.vehicle.infrastructure.inmemory.InMemoryVehicleRepository;

public class CarTrackingAppConfig {

    private static final String MQTT_HOST = "localhost";
    private static final int MQTT_PORT = 1883;

    public static RouteRegistry initRoutes() {
        // VehicleRepository를 공유 — Vehicle 상태가 Trip과 동기화되어야 하므로 동일 인스턴스
        VehicleRepository vehicleRepository = new InMemoryVehicleRepository();

        // MQTT + Simulator DI 조립
        SimulatorBootstrap simulatorBootstrap = initSimulator(vehicleRepository);

        RouteRegistry registry = new RouteRegistry();
        VehicleRouteConfig.routes(vehicleRepository).forEach(registry::add);
        TripRouteConfig.routes(vehicleRepository).forEach(registry::add);
        SimulatorRouteConfig.routes(simulatorBootstrap).forEach(registry::add);
        return registry;
    }

    private static SimulatorBootstrap initSimulator(VehicleRepository vehicleRepository) {
        MqttClientFactory factory = new MqttClientFactory(MQTT_HOST, MQTT_PORT);
        Mqtt3AsyncClient mqttClient = factory.create("cartracking-simulator");
        TelemetryPublisher publisher = new TelemetryPublisher(mqttClient);
        publisher.connect();
        return new SimulatorBootstrap(vehicleRepository, publisher);
    }
}
