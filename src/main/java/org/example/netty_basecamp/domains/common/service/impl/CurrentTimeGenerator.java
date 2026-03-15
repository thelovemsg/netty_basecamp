package org.example.netty_basecamp.domains.common.service.impl;

import org.example.netty_basecamp.domains.common.service.TimeGenerator;

public class CurrentTimeGenerator implements TimeGenerator {
    @Override
    public long millis() {
        return System.currentTimeMillis();
    }
}
