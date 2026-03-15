package org.example.netty_basecamp.fake.repository;

import org.example.netty_basecamp.domains.fare.domain.Fare;
import org.example.netty_basecamp.domains.fare.domain.FareRepository;

import java.util.HashMap;
import java.util.Map;

public class FakeFareRepository implements FareRepository {
    private final Map<Long, Fare> store = new HashMap<>();

    public void save(Fare fare) {
        store.put(fare.getId(), fare);
    }

    @Override
    public Fare findById(Long id) {
        return store.get(id);
    }
}