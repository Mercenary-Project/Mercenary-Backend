package org.example.mercenary.domain.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.common.Position;
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
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime matchDate;
    private String placeName;
    private Position position;
    private String status;
    private String writerName;

    public static AppliedMatchResponseDto from(ApplicationEntity application) {
        MatchEntity match = application.getMatch();

        return AppliedMatchResponseDto.builder()
                .applicationId(application.getId())
                .matchId(match.getId())
                .title(match.getTitle())
                .matchDate(match.getMatchDate())
                .placeName(match.getPlaceName())
                .position(application.getPosition())
                .status(application.getStatus() != null ? application.getStatus().name() : null)
                .writerName(match.getMember() != null ? match.getMember().getNickname() : null)
                .build();
    }
}
