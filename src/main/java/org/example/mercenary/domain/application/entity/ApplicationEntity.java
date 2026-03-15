package org.example.mercenary.domain.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private MatchEntity match;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.status = (this.status == null) ? ApplicationStatus.READY : this.status;
    }

    public void approve() {
        this.status = ApplicationStatus.APPROVED;
    }

    public void reject() {
        this.status = ApplicationStatus.REJECTED;
    }

    public void cancel() {
        this.status = ApplicationStatus.CANCELED;
    }
}
