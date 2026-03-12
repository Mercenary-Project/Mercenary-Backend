package org.example.mercenary.domain.application.repository;

import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {

    // 중복 신청 방지 검증을 위한 메서드
    boolean existsByMatchIdAndUserId(MatchEntity match, Long userId);
}