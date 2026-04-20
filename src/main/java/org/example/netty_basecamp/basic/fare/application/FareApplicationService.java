package org.example.netty_basecamp.basic.fare.application;

import org.example.netty_basecamp.basic.common.service.TimeGenerator;
import org.example.netty_basecamp.basic.fare.domain.Fare;
import org.example.netty_basecamp.basic.fare.domain.FareCreate;
import org.example.netty_basecamp.basic.fare.domain.FareRepository;

public class FareApplicationService {

    private final FareRepository fareRepository;
    private final TimeGenerator timeGenerator;

    public FareApplicationService(FareRepository fareRepository, TimeGenerator timeGenerator) {
        this.fareRepository = fareRepository;
        this.timeGenerator = timeGenerator;
    }

    public Fare findById(Long fareId) {
        Fare fare = fareRepository.findById(fareId);
        if (fare == null) {
            throw new IllegalArgumentException("Fare not found: " + fareId);
        }
        return fare;
    }

    public Fare create(FareCreate fareCreate) {
        Fare fare = Fare.from(fareCreate, timeGenerator.millis());
        return fareRepository.save(fare);
    }

    public void delete(Long fareId) {
        findById(fareId);
        fareRepository.deleteById(fareId);
    }
}
