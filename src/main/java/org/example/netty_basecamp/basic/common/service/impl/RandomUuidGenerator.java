package org.example.netty_basecamp.basic.common.service.impl;

import org.example.netty_basecamp.basic.common.service.UuidGenerator;

import java.util.UUID;

public class RandomUuidGenerator implements UuidGenerator {
    @Override
    public String newUuid() {
        return UUID.randomUUID().toString();
    }
}
