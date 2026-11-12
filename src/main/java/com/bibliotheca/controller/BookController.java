package com.bibliotheca.controller;

import com.bibliotheca.dto.*;
import com.bibliotheca.entity.Book;
import com.bibliotheca.entity.Bookmark;
import com.bibliotheca.entity.Note;
import com.bibliotheca.service.BookService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService bookService;
    public BookController(BookService bookService) { this.bookService = bookService; }

    @PostMapping("/upload")
    public ResponseEntity<BookDto> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookService.upload(file));
    }

    @GetMapping
    public List<BookDto> list() { return bookService.list(); }

    @GetMapping("/{id}")
    public BookDto get(@PathVariable String id) { return bookService.get(id); }

    @PatchMapping("/{id}")
    public BookDto update(@PathVariable String id, @RequestBody BookUpdateRequest req) { return bookService.update(id, req); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) { bookService.delete(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable String id) throws IOException {
        Book b = bookService.getEntity(id);
        Resource r = bookService.getFile(id);
        long len = Files.size(Path.of(b.getStoragePath()));
        String etag = "\"" + b.getId() + "-" + b.getUpdatedAt().toEpochMilli() + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(b.getMimeType() != null ? b.getMimeType() : "application/octet-stream"))
                .contentLength(len)
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(3600, TimeUnit.SECONDS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + b.getOriginalFilename().replace("\"","") + "\"")
                .body(r);
    }

    @GetMapping("/{id}/cover")
    public ResponseEntity<Resource> cover(@PathVariable String id) throws IOException {
        Book b = bookService.getEntity(id);
        Resource r = bookService.getCover(id);
        long len = Files.size(Path.of(b.getCoverPath()));
        String etag = "\"cover-" + b.getId() + "-" + b.getUpdatedAt().toEpochMilli() + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(len)
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS).cachePublic())
                .body(r);
    }

    @GetMapping("/{id}/progress")
    public BookDto.ProgressDto getProgress(@PathVariable String id) { return bookService.getProgress(id); }

    @PutMapping("/{id}/progress")
    public BookDto.ProgressDto updateProgress(@PathVariable String id, @RequestBody ProgressRequest req) { return bookService.updateProgress(id, req); }

    @GetMapping("/{id}/bookmarks")
    public List<Map<String, Object>> bookmarks(@PathVariable String id) {
        return bookService.listBookmarks(id).stream().map(this::bm).toList();
    }
    @PostMapping("/{id}/bookmarks")
    public Map<String, Object> addBookmark(@PathVariable String id, @RequestBody BookmarkRequest req) {
        return bm(bookService.addBookmark(id, req));
    }

    @GetMapping("/{id}/notes")
    public List<Map<String, Object>> notes(@PathVariable String id) {
        return bookService.listNotes(id).stream().map(this::note).toList();
    }
    @PostMapping("/{id}/notes")
    public Map<String, Object> addNote(@PathVariable String id, @RequestBody NoteRequest req) {
        return note(bookService.addNote(id, req));
    }

    @GetMapping("/{id}/search")
    public List<Map<String, Object>> search(@PathVariable String id, @RequestParam("q") String q) {
        return bookService.search(id, q);
    }

    private Map<String, Object> bm(Bookmark b) {
        return Map.of("id", b.getId(), "location", b.getLocation() == null ? "" : b.getLocation(),
                "label", b.getLabel() == null ? "" : b.getLabel(), "createdAt", b.getCreatedAt().toString());
    }
    private Map<String, Object> note(Note n) {
        return Map.of("id", n.getId(), "location", n.getLocation() == null ? "" : n.getLocation(),
                "selectedText", n.getSelectedText() == null ? "" : n.getSelectedText(),
                "content", n.getContent(), "color", n.getColor() == null ? "#ffeb3b" : n.getColor(),
                "createdAt", n.getCreatedAt().toString());
    }
}
