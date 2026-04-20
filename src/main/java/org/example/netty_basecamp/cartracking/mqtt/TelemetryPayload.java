package org.example.netty_basecamp.cartracking.mqtt;

import java.math.BigDecimal;

public record TelemetryPayload(
    Long vehicleId,
    BigDecimal latitude,
    BigDecimal longitude,
    double speed,
    String status,
    long timestamp
) {}
