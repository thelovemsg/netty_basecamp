package org.example.netty_basecamp.fake.repository;

import org.example.netty_basecamp.basic.fare.domain.FarePolicyRepository;
import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FakeFarePolicyRepository implements FarePolicyRepository {
    private final Map<Long, FarePolicy> store = new HashMap<>();
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
    public FarePolicy save(FarePolicy policy) {
        if (policy.getId() == null) {
            Long newId = sequence.getAndIncrement();
            FarePolicy withId = FarePolicy.builder()
                    .id(newId)
                    .fareId(policy.getFareId())
                    .type(policy.getType())
                    .value(policy.getValue())
                    .basis(policy.getBasis())
                    .priority(policy.getPriority())
                    .createdAt(policy.getCreatedAt())
                    .modifiedAt(policy.getModifiedAt())
                    .build();
            store.put(newId, withId);
            return withId;
        }
        store.put(policy.getId(), policy);
        return policy;
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}
