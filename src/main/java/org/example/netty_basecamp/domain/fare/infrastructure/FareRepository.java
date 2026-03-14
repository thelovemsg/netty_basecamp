package org.example.netty_basecamp.domain.fare.infrastructure;

import org.example.netty_basecamp.domain.fare.domain.Fare;

public interface FareRepository {
    Fare findById(Long id);
    void save(Fare fare);
}