package com.bibliotheca.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shelves")
public class Shelf {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "shelf_books", joinColumns = @JoinColumn(name = "shelf_id"))
    @Column(name = "book_id")
    private List<String> bookIds = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getBookIds() { return bookIds; }
    public void setBookIds(List<String> bookIds) { this.bookIds = bookIds; }
}
