package org.example.mercenary.domain.match.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.mercenary.domain.match.entity.MatchEntity;

import java.time.LocalDateTime;

@Getter
@Builder
public class MatchSearchResponseDto {
    private final Long matchId;

    private final String title;

    private final String content;

    private final String placeName;
    private final String district;
    private final LocalDateTime matchDate;
    private final Integer maxPlayerCount;
    private final Integer currentPlayerCount;

    private final String fullAddress; // 주소 정보도 있으면 좋음

    // Redis Geo 검색 시 거리 정보
    private final Double distance;

    private final Double latitude;
    private final Double longitude;

    public static MatchSearchResponseDto from(MatchEntity match, Double distance) {
        return MatchSearchResponseDto.builder()
                .matchId(match.getId())
                .title(match.getTitle())
                .content(match.getContent())

                .placeName(match.getPlaceName())
                .district(match.getDistrict())
                .matchDate(match.getMatchDate())
                .maxPlayerCount(match.getMaxPlayerCount())
                .currentPlayerCount(match.getCurrentPlayerCount())
                .fullAddress(match.getFullAddress()) // 주소 추가
                .distance(distance)
                .latitude(match.getLatitude())
                .longitude(match.getLongitude())
                .build();
    }
}