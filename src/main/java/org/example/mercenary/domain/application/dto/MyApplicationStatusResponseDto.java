package org.example.mercenary.domain.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.application.entity.ApplicationEntity;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyApplicationStatusResponseDto {

    private boolean applied;
    private String status;
    private Long applicationId;

    public static MyApplicationStatusResponseDto notApplied() {
        return MyApplicationStatusResponseDto.builder()
                .applied(false)
                .status(null)
                .applicationId(null)
                .build();
    }

    public static MyApplicationStatusResponseDto from(ApplicationEntity application) {
        return MyApplicationStatusResponseDto.builder()
                .applied(true)
                .status(application.getStatus() != null ? application.getStatus().name() : null)
                .applicationId(application.getId())
                .build();
    }
}
