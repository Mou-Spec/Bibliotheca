package com.bibliotheca.repository;
import com.bibliotheca.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BookmarkRepository extends JpaRepository<Bookmark, String> {
    List<Bookmark> findByBookIdOrderByCreatedAtDesc(String bookId);
}
