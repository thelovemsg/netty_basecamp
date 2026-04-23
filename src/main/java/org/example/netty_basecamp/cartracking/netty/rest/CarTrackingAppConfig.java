package org.example.netty_basecamp.cartracking.netty.rest;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import org.example.netty_basecamp.basic.common.service.impl.CurrentTimeGenerator;
import org.example.netty_basecamp.cartracking.mqtt.MqttClientFactory;
import org.example.netty_basecamp.cartracking.mqtt.TelemetryPublisher;
import org.example.netty_basecamp.cartracking.mqtt.VehicleTelemetrySubscriber;
import org.example.netty_basecamp.cartracking.netty.rest.config.JourneyRouteConfig;
import org.example.netty_basecamp.cartracking.netty.rest.config.SimulatorRouteConfig;
import org.example.netty_basecamp.cartracking.netty.rest.config.VehicleRouteConfig;
import org.example.netty_basecamp.cartracking.netty.rest.route.RouteRegistry;
import org.example.netty_basecamp.cartracking.simulator.SimulatorBootstrap;
import org.example.netty_basecamp.cartracking.tracking.domain.repository.JourneyRepository;
import org.example.netty_basecamp.cartracking.tracking.domain.repository.LocationSnapshotRepository;
import org.example.netty_basecamp.cartracking.tracking.infrastructure.inmemory.InMemoryJourneyRepository;
import org.example.netty_basecamp.cartracking.tracking.infrastructure.inmemory.InMemoryLocationSnapshotRepository;
import org.example.netty_basecamp.cartracking.vehicle.application.TripApplicationService;
import org.example.netty_basecamp.cartracking.vehicle.domain.repository.VehicleRepository;
import org.example.netty_basecamp.cartracking.vehicle.infrastructure.inmemory.InMemoryVehicleRepository;

public class CarTrackingAppConfig {

    private static final String MQTT_HOST = "localhost";
    private static final int MQTT_PORT = 1883;

    public static RouteRegistry initRoutes() {
        VehicleRepository vehicleRepository = new InMemoryVehicleRepository();
        JourneyRepository journeyRepository = new InMemoryJourneyRepository();
        LocationSnapshotRepository snapshotRepository = new InMemoryLocationSnapshotRepository();

        TripApplicationService tripService = new TripApplicationService(
                journeyRepository, vehicleRepository, snapshotRepository, new CurrentTimeGenerator());

        // MQTT Publisher + Simulator
        SimulatorBootstrap simulatorBootstrap = initSimulator(vehicleRepository);

        // MQTT Subscriber — 브로커로부터 telemetry 수신
        initSubscriber(tripService);

        RouteRegistry registry = new RouteRegistry();
        VehicleRouteConfig.routes(vehicleRepository).forEach(registry::add);
        JourneyRouteConfig.routes(tripService).forEach(registry::add);
        SimulatorRouteConfig.routes(simulatorBootstrap).forEach(registry::add);
        return registry;
    }

    /**
     * 차량 추적 시뮬레이터를 초기화하는 메서드
     * MQTT 연결 설정부터 시뮬레이터 부트스트랩 생성까지 담당
     *
     * @param vehicleRepository 차량 데이터 접근 레포지토리
     * @return 초기화된 SimulatorBootstrap 인스턴스
     */
    private static SimulatorBootstrap initSimulator(VehicleRepository vehicleRepository) {
        MqttClientFactory factory = new MqttClientFactory(MQTT_HOST, MQTT_PORT);
        Mqtt3AsyncClient mqttClient = factory.create("cartracking-simulator");
        TelemetryPublisher publisher = new TelemetryPublisher(mqttClient);
        publisher.connect();
        return new SimulatorBootstrap(vehicleRepository, publisher);
    }

    /**
     * 차량 텔레메트리 데이터 구독자를 초기화하는 메서드
     * MQTT 브로커로부터 차량 데이터를 수신하여 트립 서비스로 전달하는 역할
     *
     * @param tripService 수신한 텔레메트리 데이터를 처리할 트립 애플리케이션 서비스
     */
    private static void initSubscriber(TripApplicationService tripService) {
        MqttClientFactory factory = new MqttClientFactory(MQTT_HOST, MQTT_PORT);
        Mqtt3AsyncClient mqttClient = factory.create("cartracking-subscriber");
        VehicleTelemetrySubscriber subscriber = new VehicleTelemetrySubscriber(mqttClient, tripService);
        subscriber.connect();
        subscriber.subscribe();
    }
}
