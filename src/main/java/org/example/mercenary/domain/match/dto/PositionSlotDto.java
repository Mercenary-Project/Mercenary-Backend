package org.example.mercenary.domain.match.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.mercenary.domain.common.Position;

@Getter
@Setter
@NoArgsConstructor
public class PositionSlotDto {

    @NotNull(message = "포지션을 선택해 주세요.")
    private Position position;

    @NotNull(message = "모집 인원을 입력해 주세요.")
    @Min(value = 1, message = "모집 인원은 1명 이상이어야 합니다.")
    private Integer required;
}
