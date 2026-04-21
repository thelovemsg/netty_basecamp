package org.example.netty_basecamp.cartracking.tracking.domain.repository;

import org.example.netty_basecamp.cartracking.tracking.domain.Journey;
import org.example.netty_basecamp.cartracking.tracking.domain.vo.TrackingTarget;

import java.util.List;

public interface JourneyRepository {
    Journey findById(Long id);
    Journey findScheduledByTarget(TrackingTarget target);
    Journey findActiveByTarget(TrackingTarget target);
    List<Journey> findAllByTarget(TrackingTarget target);
    List<Journey> findAll();
    Journey save(Journey journey);
}
