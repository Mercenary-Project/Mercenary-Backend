package org.example.mercenary.domain.application.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications") // 신청 내역 테이블
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class) // 생성 시간 자동 기록
public class ApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 경기에 신청했는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private MatchEntity match;

    // 누가 신청했는지 (UserEntity 연결 대신 ID만 저장해도 됨, 여기선 ID로)
    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status; // 신청 상태 (READY, APPROVED, REJECTED 등)

    @CreatedDate
    private LocalDateTime createdAt; // 신청 시간

    // 생성 전 기본값
    @PrePersist
    public void prePersist() {
        this.status = (this.status == null) ? ApplicationStatus.READY : this.status;
    }
}