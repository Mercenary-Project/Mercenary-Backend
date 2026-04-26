package org.example.mercenary.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.common.SkillLevel;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "members")
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long kakaoId;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Position preferredPosition;

    @Enumerated(EnumType.STRING)
    private SkillLevel skillLevel;

    private double mannerScore;

    @Builder
    public MemberEntity(Long kakaoId, String email, String nickname, Role role) {
        this.kakaoId = kakaoId;
        this.email = email;
        this.nickname = nickname;
        this.role = (role != null) ? role : Role.USER;
        this.mannerScore = 0.0;
    }
}
