package org.example.mercenary.domain.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.match.entity.MatchPositionSlot;

@Getter
@Builder
@NoArgsConstructor  // 👈 추가: Redis(Jackson) 역직렬화를 위한 기본 생성자
@AllArgsConstructor // 👈 추가: @Builder가 정상 작동하기 위해 필요한 생성자

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
