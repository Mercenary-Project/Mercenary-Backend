package org.example.mercenary.domain.application.repository;

import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {

    boolean existsByMatchAndUserId(MatchEntity match, Long userId);

    Optional<ApplicationEntity> findByMatchAndUserId(MatchEntity match, Long userId);

    List<ApplicationEntity> findAllByMatchOrderByCreatedAtAsc(MatchEntity match);

    Optional<ApplicationEntity> findByIdAndMatch(Long applicationId, MatchEntity match);
}
