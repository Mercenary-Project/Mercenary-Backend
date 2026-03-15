package org.example.mercenary.domain.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.match.entity.MatchEntity;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppliedMatchResponseDto {

    private Long applicationId;
    private Long matchId;
    private String title;
    private LocalDateTime matchDate;
    private String placeName;
    private String status;
    private Integer currentPlayerCount;
    private Integer maxPlayerCount;
    private String writerName;

    public static AppliedMatchResponseDto from(ApplicationEntity application) {
        MatchEntity match = application.getMatch();

        return AppliedMatchResponseDto.builder()
                .applicationId(application.getId())
                .matchId(match.getId())
                .title(match.getTitle())
                .matchDate(match.getMatchDate())
                .placeName(match.getPlaceName())
                .status(application.getStatus() != null ? application.getStatus().name() : null)
                .currentPlayerCount(match.getCurrentPlayerCount())
                .maxPlayerCount(match.getMaxPlayerCount())
                .writerName(match.getMember() != null ? match.getMember().getNickname() : null)
                .build();
    }
}
