package org.example.mercenary.domain.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.example.mercenary.domain.common.Position;


@Getter
@Setter
public class ApplicationRequestDto {

    @NotNull(message = "포지션을 선택해 주세요.")
    private Position position;
}
