package com.bibliotheca.repository;
import com.bibliotheca.entity.ReadingProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, String> {
    Optional<ReadingProgress> findByBookId(String bookId);
}
