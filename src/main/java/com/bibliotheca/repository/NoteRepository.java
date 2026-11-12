package com.bibliotheca.repository;
import com.bibliotheca.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface NoteRepository extends JpaRepository<Note, String> {
    List<Note> findByBookIdOrderByCreatedAtDesc(String bookId);
}
