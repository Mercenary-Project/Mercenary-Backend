package org.example.mercenary.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "members")
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 카카오에서 제공하는 고유 번호 (회원 식별 핵심 키)
    @Column(nullable = false, unique = true)
    private Long kakaoId;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder
    public MemberEntity(Long kakaoId, String email, String nickname, Role role) {
        this.kakaoId = kakaoId;
        this.email = email;
        this.nickname = nickname;
        this.role = (role != null) ? role : Role.USER;
    }
}