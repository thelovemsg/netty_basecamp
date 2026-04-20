package org.example.netty_basecamp.cartracking.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

public class MqttClientFactory {

    private final String host;
    private final int port;

    public MqttClientFactory(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Mqtt3AsyncClient create(String clientId) {
        return MqttClient.builder()
                .useMqttVersion3()
                .serverHost(host)
                .serverPort(port)
                .identifier(clientId)
                .automaticReconnect().applyAutomaticReconnect()
                .buildAsync();
    }
}
