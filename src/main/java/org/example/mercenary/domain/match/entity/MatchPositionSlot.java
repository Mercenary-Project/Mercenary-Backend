package org.example.mercenary.domain.match.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.common.Position;

@Entity
@Table(name = "match_position_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchPositionSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private MatchEntity match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Position position;

    @Column(nullable = false)
    private int required;

    @Column(nullable = false)
    private int filled;

    public static MatchPositionSlot of(MatchEntity match, Position position, int required) {
        MatchPositionSlot slot = new MatchPositionSlot();
        slot.match = match;
        slot.position = position;
        slot.required = required;
        slot.filled = 0;
        return slot;
    }

    public boolean isAvailable() {
        return filled < required;
    }

    public void increaseFilled() {
        this.filled++;
    }

    public void decreaseFilled() {
        if (this.filled > 0) {
            this.filled--;
        }
    }
}
