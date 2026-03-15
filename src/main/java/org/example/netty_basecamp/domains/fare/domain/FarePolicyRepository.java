package org.example.netty_basecamp.domains.fare.domain;

import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicy;

import java.util.List;

public interface FarePolicyRepository {
    List<FarePolicy> findByFareId(Long fareId);
    void save(FarePolicy farePolicy);
}
