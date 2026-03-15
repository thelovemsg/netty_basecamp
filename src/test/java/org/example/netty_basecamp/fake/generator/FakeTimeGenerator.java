package org.example.netty_basecamp.fake.generator;

import org.example.netty_basecamp.domain.common.service.TimeGenerator;

public class FakeTimeGenerator implements TimeGenerator {

    private final long millis;

    public FakeTimeGenerator(long millis) {
        this.millis = millis;
    }

    @Override
    public long millis() {
        return millis;
    }
}
