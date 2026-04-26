package org.example.mercenary.domain.common;

public enum Position {
    GK("골키퍼"),
    CB("센터백"),
    LB("왼쪽 풀백"),
    RB("오른쪽 풀백"),
    CDM("수비형 미드필더"),
    CM("중앙 미드필더"),
    CAM("공격형 미드필더"),
    LW("왼쪽 윙어"),
    RW("오른쪽 윙어"),
    ST("스트라이커");

    private final String label;

    Position(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
