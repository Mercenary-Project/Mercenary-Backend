package org.example.mercenary.domain.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.member.entity.MemberEntity;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSummaryResponseDto {

    private Long applicationId;
    private Long applicantId;
    private String applicantNickname;
    private String status;
    private LocalDateTime createdAt;

    public static ApplicationSummaryResponseDto from(ApplicationEntity application, MemberEntity member) {
        return ApplicationSummaryResponseDto.builder()
                .applicationId(application.getId())
                .applicantId(application.getUserId())
                .applicantNickname(member != null ? member.getNickname() : "알 수 없음")
                .status(application.getStatus() != null ? application.getStatus().name() : null)
                .createdAt(application.getCreatedAt())
                .build();
    }
}
