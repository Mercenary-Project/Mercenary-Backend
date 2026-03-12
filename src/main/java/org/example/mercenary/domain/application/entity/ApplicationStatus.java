package org.example.mercenary.domain.application.entity;

public enum ApplicationStatus {
    READY,      // 대기 중
    APPROVED,   // 승인됨
    REJECTED,   // 거절됨
    CANCELED    // 취소함
}