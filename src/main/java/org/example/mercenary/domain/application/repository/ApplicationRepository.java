package org.example.mercenary.domain.application.repository;

import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {

    boolean existsByMatchAndUserId(MatchEntity match, Long userId);

    boolean existsByMatchAndUserIdAndPosition(MatchEntity match, Long userId, Position position);

    Optional<ApplicationEntity> findByMatchAndUserId(MatchEntity match, Long userId);

    List<ApplicationEntity> findAllByMatchOrderByCreatedAtAsc(MatchEntity match);

    void deleteAllByMatch(MatchEntity match);

    Optional<ApplicationEntity> findByIdAndMatch(Long applicationId, MatchEntity match);

    @Query("""
            select application
            from ApplicationEntity application
            join fetch application.match match
            left join fetch match.member member
            where application.userId = :userId
            order by application.createdAt desc
            """)
    List<ApplicationEntity> findAppliedMatchesByUserId(@Param("userId") Long userId);
}
