package org.example.mercenary.domain.match.repository;

import java.util.List;
import java.util.Optional;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.entity.MatchPositionSlot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchPositionSlotRepository extends JpaRepository<MatchPositionSlot, Long> {

    Optional<MatchPositionSlot> findByMatchAndPosition(MatchEntity match, Position position);

    List<MatchPositionSlot> findAllByMatch(MatchEntity match);
}
