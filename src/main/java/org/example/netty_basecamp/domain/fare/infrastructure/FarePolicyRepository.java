package org.example.netty_basecamp.domain.fare.infrastructure;

import org.example.netty_basecamp.domain.fare.domain.policy.FarePolicy;

import java.util.List;

public interface FarePolicyRepository {
    List<FarePolicy> findByFareId(Long fareId);
    void save(FarePolicy farePolicy);
}