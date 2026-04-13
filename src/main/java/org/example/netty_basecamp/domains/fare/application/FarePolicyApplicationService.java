package org.example.netty_basecamp.domains.fare.application;

import org.example.netty_basecamp.domains.common.service.TimeGenerator;
import org.example.netty_basecamp.domains.fare.domain.FarePolicyRepository;
import org.example.netty_basecamp.domains.fare.domain.FareRepository;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicy;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicyCreate;
import org.example.netty_basecamp.domains.fare.domain.policy.FarePolicyUpdate;

import java.util.List;

public class FarePolicyApplicationService {

    private final FareRepository fareRepository;
    private final FarePolicyRepository farePolicyRepository;
    private final TimeGenerator timeGenerator;

    public FarePolicyApplicationService(FareRepository fareRepository, FarePolicyRepository farePolicyRepository,
                                        TimeGenerator timeGenerator) {
        this.fareRepository = fareRepository;
        this.farePolicyRepository = farePolicyRepository;
        this.timeGenerator = timeGenerator;
    }

    public List<FarePolicy> findByFareId(Long fareId) {
        validateFareExists(fareId);
        return farePolicyRepository.findByFareId(fareId);
    }

    public FarePolicy findById(Long fareId, Long policyId) {
        FarePolicy policy = farePolicyRepository.findById(policyId);
        if (policy == null) {
            throw new IllegalArgumentException("FarePolicy not found: " + policyId);
        }
        if (!policy.getFareId().equals(fareId)) {
            throw new IllegalArgumentException("Policy " + policyId + " does not belong to fare " + fareId);
        }
        return policy;
    }

    public FarePolicy add(Long fareId, FarePolicyCreate policyCreate) {
        validateFareExists(fareId);
        FarePolicy policy = FarePolicy.from(policyCreate, fareId, timeGenerator.millis());
        return farePolicyRepository.save(policy);
    }

    public FarePolicy update(Long fareId, Long policyId, FarePolicyUpdate policyUpdate) {
        FarePolicy policy = findById(fareId, policyId);
        FarePolicy updated = policy.update(policyUpdate, timeGenerator.millis());
        return farePolicyRepository.save(updated);
    }

    public void delete(Long fareId, Long policyId) {
        findById(fareId, policyId); // 존재 + 소속 검증
        farePolicyRepository.deleteById(policyId);
    }

    private void validateFareExists(Long fareId) {
        if (fareRepository.findById(fareId) == null) {
            throw new IllegalArgumentException("Fare not found: " + fareId);
        }
    }
}
