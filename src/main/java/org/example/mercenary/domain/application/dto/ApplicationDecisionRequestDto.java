package org.example.mercenary.domain.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.example.mercenary.domain.application.entity.ApplicationStatus;

@Getter
@Setter
public class ApplicationDecisionRequestDto {

    @Schema(description = "처리 상태", example = "APPROVED", allowableValues = {"APPROVED", "REJECTED"})
    @NotNull(message = "처리 상태를 입력해 주세요.")
    private ApplicationStatus status;
}
