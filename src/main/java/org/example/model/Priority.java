package org.example.model;

public enum Priority {
    // Градация уровней важности задач
    HIGH("🔴 Высокий"),
    MEDIUM("🟡 Средний"),
    LOW("🟢 Низкий");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}