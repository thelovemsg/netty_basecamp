package org.example.netty_basecamp.domains.fare.domain;

public interface FareRepository {
    Fare findById(Long id);
    Fare save(Fare fare);
    void deleteById(Long id);
}
