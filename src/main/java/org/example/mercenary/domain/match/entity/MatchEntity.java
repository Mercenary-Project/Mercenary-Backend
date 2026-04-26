package org.example.mercenary.domain.match.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.match.dto.MatchUpdateRequestDto;
import org.example.mercenary.domain.match.dto.PositionSlotDto;
import org.example.mercenary.domain.member.entity.MemberEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    private double latitude;
    private double longitude;
    private String fullAddress;

    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    private int viewCount;
    private int chatCount;

    // DB 호환 컬럼 — 포지션 슬롯으로 전환 후 미사용, 기존 스키마 NOT NULL 제약 유지용
    @Column(nullable = false)
    private int maxPlayerCount;

    @Column(nullable = false)
    private int currentPlayerCount;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MatchPositionSlot> slots = new ArrayList<>();

    @Builder
    public MatchEntity(Long id, MemberEntity member, String title, String content, String placeName,
                       String district, LocalDateTime matchDate, double latitude, double longitude,
                       String fullAddress, MatchStatus status, int viewCount, int chatCount) {
        this.id = id;
        this.member = member;
        this.title = title;
        this.content = content;
        this.placeName = placeName;
        this.district = district;
        this.matchDate = matchDate;
        this.latitude = latitude;
        this.longitude = longitude;
        this.fullAddress = fullAddress;
        this.status = status;
        this.viewCount = viewCount;
        this.chatCount = chatCount;
        this.slots = new ArrayList<>();
    }

    @PrePersist
    public void prePersist() {
        this.status = (this.status == null) ? MatchStatus.RECRUITING : this.status;
    }

    public static MatchEntity from(MatchCreateRequestDto request, MemberEntity member) {
        return MatchEntity.builder()
                .member(member)
                .title(request.getTitle())
                .content(request.getContent())
                .placeName(request.getPlaceName())
                .district(request.getDistrict())
                .matchDate(request.getMatchDate())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .fullAddress(request.getFullAddress())
                .build();
    }

    public boolean isFullyBooked() {
        if (slots.isEmpty()) {
            return false;
        }
        return slots.stream().noneMatch(MatchPositionSlot::isAvailable);
    }

    public MatchPositionSlot getSlot(Position position) {
        return slots.stream()
                .filter(slot -> slot.getPosition() == position)
                .findFirst()
                .orElse(null);
    }

    public void close() {
        this.status = MatchStatus.CLOSED;
    }

    public void update(MatchUpdateRequestDto request) {
        this.title = request.getTitle();
        this.content = request.getContent();
        this.placeName = request.getPlaceName();
        this.district = request.getDistrict();
        this.matchDate = request.getMatchDate();
        this.latitude = request.getLatitude();
        this.longitude = request.getLongitude();
        this.fullAddress = request.getFullAddress();
    }

    public void updateSlots(List<PositionSlotDto> slotDtos) {
        this.slots.clear();
        for (PositionSlotDto dto : slotDtos) {
            this.slots.add(MatchPositionSlot.of(this, dto.getPosition(), dto.getRequired()));
        }
    }
}
