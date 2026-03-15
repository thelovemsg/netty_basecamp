package org.example.netty_basecamp.domains.fare.domain;

public interface FareRepository {
    Fare findById(Long id);
    void save(Fare fare);
}
