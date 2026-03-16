package org.example.mercenary.domain.match.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.match.dto.MatchUpdateRequestDto;
import org.example.mercenary.domain.member.entity.MemberEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Access(AccessType.FIELD)
public class MatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private MemberEntity member;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private String placeName;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private LocalDateTime matchDate;

    @Column(nullable = false)
    private int maxPlayerCount;

    @Column(nullable = false)
    private int currentPlayerCount;

    private double latitude;
    private double longitude;
    private String fullAddress;

    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    private int viewCount;
    private int chatCount;

    @PrePersist
    public void prePersist() {
        this.status = (this.status == null) ? MatchStatus.RECRUITING : this.status;
        this.currentPlayerCount = (this.currentPlayerCount == 0) ? 1 : this.currentPlayerCount;
    }

    public static MatchEntity from(MatchCreateRequestDto request, MemberEntity member) {
        return MatchEntity.builder()
                .member(member)
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
        if (this.currentPlayerCount >= this.maxPlayerCount) {
            this.status = MatchStatus.CLOSED;
        }
    }

    public void update(MatchUpdateRequestDto request) {
        this.title = request.getTitle();
        this.content = request.getContent();
        this.placeName = request.getPlaceName();
        this.district = request.getDistrict();
        this.matchDate = request.getMatchDate();
        this.maxPlayerCount = request.getMaxPlayerCount();
        this.currentPlayerCount = request.getCurrentPlayerCount();
        this.latitude = request.getLatitude();
        this.longitude = request.getLongitude();
        this.fullAddress = request.getFullAddress();
        this.status = this.currentPlayerCount >= this.maxPlayerCount
                ? MatchStatus.CLOSED
                : MatchStatus.RECRUITING;
    }
}
