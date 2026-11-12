package com.bibliotheca.repository;
import com.bibliotheca.entity.Shelf;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ShelfRepository extends JpaRepository<Shelf, String> {}
