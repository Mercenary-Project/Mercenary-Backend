package org.example.mercenary.domain.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.match.entity.MatchPositionSlot;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class PositionSlotResponseDto {

    private Position position;
    private String positionLabel;
    private int required;
    private int filled;
    private int available;

    public static PositionSlotResponseDto from(MatchPositionSlot slot) {
        return PositionSlotResponseDto.builder()
                .position(slot.getPosition())
                .positionLabel(slot.getPosition().getLabel())
                .required(slot.getRequired())
                .filled(slot.getFilled())
                .available(slot.getRequired() - slot.getFilled())
                .build();
    }
}
