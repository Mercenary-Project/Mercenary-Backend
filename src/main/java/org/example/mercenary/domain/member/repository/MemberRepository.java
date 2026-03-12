package org.example.mercenary.domain.member.repository;

import org.example.mercenary.domain.member.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {
    // 카카오 ID로 기존 회원인지 확인하기 위해 필요합니다.
    Optional<MemberEntity> findByKakaoId(Long kakaoId);
}