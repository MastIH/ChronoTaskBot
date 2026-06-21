package org.example.model;

import java.time.LocalDateTime;

public class Task {
    private Long chatId;
    private String title;
    private Priority priority;
    private LocalDateTime deadline;
    private boolean isCompleted;

    public Task() {
    }

    public Task(Long chatId) {
        this.chatId = chatId;
        this.isCompleted = false;
    }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }
}