package org.example.netty_basecamp.domains.fare.infrastructure;

import org.example.netty_basecamp.domains.fare.domain.FarePolicyRepository;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryFarePolicyRepository implements FarePolicyRepository {

    private final Map<Long, FarePolicy> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public FarePolicy findById(Long id) {
        return store.get(id);
    }

    @Override
    public List<FarePolicy> findByFareId(Long fareId) {
        return store.values().stream()
                .filter(p -> p.getFareId().equals(fareId))
                .toList();
    }

    @Override
    public FarePolicy save(FarePolicy farePolicy) {
        if (farePolicy.getId() == null) {
            Long newId = sequence.getAndIncrement();
            FarePolicy withId = FarePolicy.builder()
                    .id(newId)
                    .fareId(farePolicy.getFareId())
                    .type(farePolicy.getType())
                    .value(farePolicy.getValue())
                    .basis(farePolicy.getBasis())
                    .priority(farePolicy.getPriority())
                    .createdAt(farePolicy.getCreatedAt())
                    .modifiedAt(farePolicy.getModifiedAt())
                    .build();
            store.put(newId, withId);
            return withId;
        }
        store.put(farePolicy.getId(), farePolicy);
        return farePolicy;
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}
