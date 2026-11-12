package com.bibliotheca.repository;
import com.bibliotheca.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BookRepository extends JpaRepository<Book, String> {}
