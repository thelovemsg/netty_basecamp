package org.example.netty_basecamp.cartracking.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.Location;
import org.example.netty_basecamp.cartracking.vehicle.application.TripApplicationService;

public class VehicleTelemetrySubscriber {

    private static final Logger log = LogManager.getLogger(VehicleTelemetrySubscriber.class);

    private final Mqtt3AsyncClient client;
    private final TripApplicationService tripApplicationService;
    private final ObjectMapper objectMapper;

    public VehicleTelemetrySubscriber(Mqtt3AsyncClient client,
                                      TripApplicationService tripApplicationService) {
        this.client = client;
        this.tripApplicationService = tripApplicationService;
        this.objectMapper = new ObjectMapper();
    }

    public void connect() {
        client.connect().join();
        log.info("MQTT Subscriber 브로커에 연결되었습니다.");
    }

    public void subscribe() {
        client.subscribeWith()
                .topicFilter("vehicle/+/telemetry")
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(publish -> {
                    try {
                        byte[] payload = publish.getPayloadAsBytes();
                        TelemetryPayload telemetry = objectMapper.readValue(payload, TelemetryPayload.class);

                        Location location = Location.of(telemetry.latitude(), telemetry.longitude());
                        tripApplicationService.recordSnapshot(telemetry.vehicleId(), location);

                        log.info("Telemetry 수신 [vehicleId={}] [lat={}, lng={}]",
                                telemetry.vehicleId(), telemetry.latitude(), telemetry.longitude());
                    } catch (IllegalStateException e) {
                        log.warn("Snapshot 기록 실패 (진행 중인 운행 없음): {}", e.getMessage());
                    } catch (Exception e) {
                        log.error("Telemetry 처리 실패: {}", e.getMessage(), e);
                    }
                })
                .send()
                .whenComplete((ack, error) -> {
                    if (error != null) {
                        log.error("MQTT subscribe 실패: {}", error.getMessage());
                    } else {
                        log.info("MQTT subscribe 성공 [topic=vehicle/+/telemetry]");
                    }
                });
    }

    public void disconnect() {
        client.disconnect().join();
        log.info("MQTT Subscriber 연결이 해제되었습니다.");
    }
}
