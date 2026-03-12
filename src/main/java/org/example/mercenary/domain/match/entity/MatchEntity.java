package org.example.mercenary.domain.match.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.member.entity.MemberEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "matches") // DB 테이블 이름 (match는 예약어일 수 있어서 matches로)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long id;

    // 작성자 (누가 썼는지 알아야 하니까 Member와 연결)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private MemberEntity member;

    @Column(nullable = false)
    private String title; // 제목 (예: 잠실 6vs6)

    @Column(nullable = false)
    private String content; // 본문 내용

    @Column(nullable = false)
    private String placeName; // 장소 이름 (예: 잠실풋살장)

    @Column(nullable = false)
    private String district; // 구 이름 (예: 송파구) - 필터링용

    @Column(nullable = false)
    private LocalDateTime matchDate; // 경기 날짜 및 시간

    @Column(nullable = false)
    private int maxPlayerCount; // 최대 인원 (예: 12명)

    @Column(nullable = false)
    private int currentPlayerCount; // 현재 인원 (기본 1명부터 시작)

    private double latitude; // 위도 (지도 표시용)
    private double longitude; // 경도 (지도 표시용)

    private String fullAddress; // 전체 주소

    @Enumerated(EnumType.STRING)
    private MatchStatus status; // 모집중(RECRUITING), 마감(CLOSED) 등

    private int viewCount; // 조회수
    private int chatCount; // 채팅수

    // 생성될 때 기본값 설정
    @PrePersist
    public void prePersist() {
        this.status = (this.status == null) ? MatchStatus.RECRUITING : this.status;
        this.currentPlayerCount = (this.currentPlayerCount == 0) ? 1 : this.currentPlayerCount;
    }

    public static MatchEntity from(MatchCreateRequestDto request, MemberEntity member) {
        return MatchEntity.builder()
                .member(member) // 작성자 연결
                .title(request.getTitle())
                .content(request.getContent())
                .placeName(request.getPlaceName())
                .district(request.getDistrict())
                .matchDate(request.getMatchDate())
                .maxPlayerCount(request.getMaxPlayerCount())
                .currentPlayerCount(request.getCurrentPlayerCount())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .fullAddress(request.getFullAddress())
                .build();
        }
    public void increasePlayerCount() {
        this.currentPlayerCount++;
    }
}