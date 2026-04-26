package org.example.mercenary.domain.match.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import org.example.mercenary.domain.match.entity.MatchEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Jacksonized
@Getter
@Builder
public class MatchSearchResponseDto {
    private final Long matchId;
    private final String title;
    private final String content;
    private final String placeName;
    private final String district;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime matchDate;
    private final String fullAddress;
    private final Double distance;
    private final Double latitude;
    private final Double longitude;
    private final List<PositionSlotResponseDto> slots;
    private final boolean isFullyBooked;

    public static MatchSearchResponseDto from(MatchEntity match, Double distance) {
        return MatchSearchResponseDto.builder()
                .matchId(match.getId())
                .title(match.getTitle())
                .content(match.getContent())
                .placeName(match.getPlaceName())
                .district(match.getDistrict())
                .matchDate(match.getMatchDate())
                .fullAddress(match.getFullAddress())
                .distance(distance)
                .latitude(match.getLatitude())
                .longitude(match.getLongitude())
                .slots(match.getSlots().stream()
                        .map(PositionSlotResponseDto::from)
                        .collect(Collectors.toList()))
                .isFullyBooked(match.isFullyBooked())
                .build();
    }
}
