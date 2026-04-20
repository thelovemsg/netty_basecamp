package org.example.netty_basecamp.basic.fare.domain;

import org.example.netty_basecamp.basic.fare.domain.policy.FarePolicy;

import java.util.List;

public interface FarePolicyRepository {
    FarePolicy findById(Long id);
    List<FarePolicy> findByFareId(Long fareId);
    FarePolicy save(FarePolicy farePolicy);
    void deleteById(Long id);
}
