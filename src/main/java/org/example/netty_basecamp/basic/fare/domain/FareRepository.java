package org.example.netty_basecamp.basic.fare.domain;

public interface FareRepository {
    Fare findById(Long id);
    Fare save(Fare fare);
    void deleteById(Long id);
}
