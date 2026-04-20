package org.example.netty_basecamp.basic;

import org.example.netty_basecamp.basic.common.service.TimeGenerator;
import org.example.netty_basecamp.fake.generator.FakeTimeGenerator;
import org.junit.jupiter.api.BeforeEach;

class FareTest {

    private TimeGenerator createTimeGenerator;
    private TimeGenerator modifyTimeGenerator;

    @BeforeEach
    public void setup() {
        createTimeGenerator = new FakeTimeGenerator(1000L);
        modifyTimeGenerator = new FakeTimeGenerator(9999L);
    }
}