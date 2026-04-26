package org.example.mercenary.domain.common;

public enum SkillLevel {
    BEGINNER("입문"),
    AMATEUR("아마추어"),
    SEMI_PRO("세미프로");

    private final String label;

    SkillLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
