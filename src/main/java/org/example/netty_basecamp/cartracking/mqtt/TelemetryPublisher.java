package org.example.netty_basecamp.cartracking.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TelemetryPublisher {

    private static final Logger log = LogManager.getLogger(TelemetryPublisher.class);

    private final Mqtt3AsyncClient client;
    private final ObjectMapper objectMapper;

    public TelemetryPublisher(Mqtt3AsyncClient client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    public void connect() {
        client.connect().join();
        log.info("MQTT 브로커에 연결되었습니다.");
    }

    public void publish(TelemetryPayload payload) {
        try {
            String topic = "vehicle/" + payload.vehicleId() + "/telemetry";
            String jsonStr = objectMapper.writeValueAsString(payload);
            client.publishWith()
                    .topic(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .payload(jsonStr.getBytes())
                    .send()
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("MQTT publish 실패 [vehicle={}]: {}", payload.vehicleId(), error.getMessage());
                        } else {
                            log.info("MQTT publish [topic={}] [payload={}]", topic, jsonStr);
                        }
                    });
        } catch (Exception e) {
            log.error("Telemetry 직렬화 실패: {}", e.getMessage());
        }
    }

    public void disconnect() {
        client.disconnect().join();
        log.info("MQTT 브로커 연결이 해제되었습니다.");
    }
}
