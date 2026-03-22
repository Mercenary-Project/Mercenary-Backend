package org.example.mercenary.domain.match.repository;

import org.example.mercenary.domain.match.entity.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchRepository extends JpaRepository<MatchEntity, Long> {
    List<MatchEntity> findAllByMemberIdOrderByMatchDateDesc(Long memberId);
    List<MatchEntity> findAllByMatchDateBefore(LocalDateTime matchDate);
}
