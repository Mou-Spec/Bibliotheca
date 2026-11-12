package com.bibliotheca.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reading_progress")
public class ReadingProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne
    @JoinColumn(name = "book_id", nullable = false, unique = true)
    private Book book;

    private String location; // page number or epub cfi
    private Double percent;
    private String status; // NOT_STARTED, READING, FINISHED
    private Instant updatedAt;

    @PrePersist @PreUpdate
    public void touch() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Double getPercent() { return percent; }
    public void setPercent(Double percent) { this.percent = percent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
