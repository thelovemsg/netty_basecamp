package org.example.netty_basecamp.fake.repository;

import org.example.netty_basecamp.domains.fare.domain.Fare;
import org.example.netty_basecamp.domains.fare.domain.FareRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FakeFareRepository implements FareRepository {
    private final Map<Long, Fare> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Fare findById(Long id) {
        return store.get(id);
    }

    @Override
    public Fare save(Fare fare) {
        if (fare.getId() == null) {
            Long newId = sequence.getAndIncrement();
            Fare withId = Fare.builder()
                    .id(newId)
                    .name(fare.getName())
                    .basePrice(fare.getBasePrice())
                    .status(fare.getStatus())
                    .fareType(fare.getFareType())
                    .createdAt(fare.getCreatedAt())
                    .modifiedAt(fare.getModifiedAt())
                    .build();
            store.put(newId, withId);
            return withId;
        }
        store.put(fare.getId(), fare);
        return fare;
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}
