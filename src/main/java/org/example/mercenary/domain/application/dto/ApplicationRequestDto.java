package org.example.mercenary.domain.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ApplicationRequestDto {

    // @NotNull을 통해 입력값 유효성 검사를 합니다.
    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

}