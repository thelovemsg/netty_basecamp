package org.example.netty_basecamp.cartracking.netty.rest;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
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
import org.example.netty_basecamp.cartracking.simulator.SeoulRouteGenerator;
import org.example.netty_basecamp.cartracking.vehicle.application.TripApplicationService;
import org.example.netty_basecamp.cartracking.vehicle.domain.Vehicle;
import org.example.netty_basecamp.cartracking.vehicle.domain.dto.VehicleCreate;
import org.example.netty_basecamp.cartracking.vehicle.domain.enums.VehicleTypeEnum;
import org.example.netty_basecamp.cartracking.vehicle.domain.repository.VehicleRepository;
import org.example.netty_basecamp.cartracking.vehicle.infrastructure.inmemory.InMemoryVehicleRepository;

public class CarTrackingAppConfig {

    private static final String MQTT_HOST = "localhost";
    private static final int MQTT_PORT = 1883;

    public record BootstrapResult(RouteRegistry routeRegistry, ChannelGroup websocketClients) {}

    private static final int SEED_VEHICLE_COUNT = 1000;
    private static final VehicleTypeEnum[] TYPES = VehicleTypeEnum.values();

    public static BootstrapResult init() {
        VehicleRepository vehicleRepository = new InMemoryVehicleRepository();
        JourneyRepository journeyRepository = new InMemoryJourneyRepository();
        LocationSnapshotRepository snapshotRepository = new InMemoryLocationSnapshotRepository();

        TripApplicationService tripService = new TripApplicationService(
                journeyRepository, vehicleRepository, snapshotRepository, new CurrentTimeGenerator());

        // 차량 10대 미리 등록 (서울 영역 내 랜덤 출발지)
        seedVehicles(vehicleRepository);

        // WebSocket 연결 클라이언트 목록
        ChannelGroup websocketClients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

        // MQTT Publisher + Simulator
        SimulatorBootstrap simulatorBootstrap = initSimulator(vehicleRepository, tripService);

        // MQTT Subscriber — 브로커로부터 telemetry 수신 + WebSocket broadcast
        initSubscriber(tripService, websocketClients);

        RouteRegistry registry = new RouteRegistry();
        VehicleRouteConfig.routes(vehicleRepository).forEach(registry::add);
        JourneyRouteConfig.routes(tripService).forEach(registry::add);
        SimulatorRouteConfig.routes(simulatorBootstrap).forEach(registry::add);
        return new BootstrapResult(registry, websocketClients);
    }

    /**
     * 차량 추적 시뮬레이터를 초기화하는 메서드
     * MQTT 연결 설정부터 시뮬레이터 부트스트랩 생성까지 담당
     *
     * @param vehicleRepository 차량 데이터 접근 레포지토리
     * @return 초기화된 SimulatorBootstrap 인스턴스
     */
    private static SimulatorBootstrap initSimulator(VehicleRepository vehicleRepository,
                                                       TripApplicationService tripService) {
        MqttClientFactory factory = new MqttClientFactory(MQTT_HOST, MQTT_PORT);
        Mqtt3AsyncClient mqttClient = factory.create("cartracking-simulator");
        TelemetryPublisher publisher = new TelemetryPublisher(mqttClient);
        publisher.connect();
        return new SimulatorBootstrap(vehicleRepository, publisher, tripService);
    }

    /**
     * 차량 텔레메트리 데이터 구독자를 초기화하는 메서드
     * MQTT 브로커로부터 차량 데이터를 수신하여 트립 서비스로 전달하는 역할
     */
    private static void seedVehicles(VehicleRepository repository) {
        SeoulRouteGenerator routeGen = new SeoulRouteGenerator();
        long now = System.currentTimeMillis();
        for (int i = 1; i <= SEED_VEHICLE_COUNT; i++) {
            String plateNumber = String.format("TEST-%04d", i);
            VehicleCreate create = VehicleCreate.builder()
                    .plateNumber(plateNumber)
                    .type(TYPES[i % TYPES.length])
                    .homeLocation(routeGen.randomLocation())
                    .build();
            repository.save(Vehicle.create(create, now));
        }
    }

    private static void initSubscriber(TripApplicationService tripService, ChannelGroup websocketClients) {
        MqttClientFactory factory = new MqttClientFactory(MQTT_HOST, MQTT_PORT);
        Mqtt3AsyncClient mqttClient = factory.create("cartracking-subscriber");
        VehicleTelemetrySubscriber subscriber = new VehicleTelemetrySubscriber(mqttClient, tripService, websocketClients);
        subscriber.connect();
        subscriber.subscribe();
    }
}
