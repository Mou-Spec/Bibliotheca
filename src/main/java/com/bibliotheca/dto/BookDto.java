package com.bibliotheca.dto;

import com.bibliotheca.entity.Book;
import com.bibliotheca.entity.ReadingProgress;
import java.time.Instant;

public record BookDto(
        String id, String title, String author, String extension, String mimeType,
        String originalFilename, String tags, Long fileSize, Integer totalPages,
        String description, Instant createdAt, Instant updatedAt,
        boolean hasCover, ProgressDto progress
) {
    public static BookDto from(Book b) {
        ProgressDto p = null;
        ReadingProgress rp = b.getProgress();
        if (rp != null) p = new ProgressDto(rp.getLocation(), rp.getPercent(), rp.getStatus(), rp.getUpdatedAt());
        return new BookDto(b.getId(), b.getTitle(), b.getAuthor(), b.getExtension(), b.getMimeType(),
                b.getOriginalFilename(), b.getTags(), b.getFileSize(), b.getTotalPages(),
                b.getDescription(), b.getCreatedAt(), b.getUpdatedAt(),
                b.getCoverPath() != null, p);
    }
    public record ProgressDto(String location, Double percent, String status, Instant updatedAt) {}
}
