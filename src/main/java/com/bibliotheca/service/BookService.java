package com.bibliotheca.service;

import com.bibliotheca.dto.*;
import com.bibliotheca.entity.*;
import com.bibliotheca.exception.BadRequestException;
import com.bibliotheca.exception.NotFoundException;
import com.bibliotheca.repository.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookService {
    private final BookRepository bookRepository;
    private final ReadingProgressRepository progressRepository;
    private final BookmarkRepository bookmarkRepository;
    private final NoteRepository noteRepository;
    private final StorageService storageService;
    private final MetadataService metadataService;

    public BookService(BookRepository bookRepository, ReadingProgressRepository progressRepository,
                       BookmarkRepository bookmarkRepository, NoteRepository noteRepository,
                       StorageService storageService, MetadataService metadataService) {
        this.bookRepository = bookRepository;
        this.progressRepository = progressRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.noteRepository = noteRepository;
        this.storageService = storageService;
        this.metadataService = metadataService;
    }

    @Transactional
    public BookDto upload(MultipartFile file) throws IOException {
        StorageService.StoredFile stored = storageService.store(file);
        Book book = new Book();
        book.setId(stored.uuid());
        book.setOriginalFilename(stored.originalName());
        book.setExtension(stored.ext());
        book.setMimeType(stored.mimeType() != null ? stored.mimeType() : guessMime(stored.ext()));
        book.setStoragePath(stored.path());
        book.setFileSize(stored.size());
        String name = stored.originalName();
        int dot = name.lastIndexOf('.');
        book.setTitle(dot > 0 ? name.substring(0, dot) : name);
        book = bookRepository.save(book);
        // enrich
        metadataService.enrichAndExtractCover(book, new File(stored.path()));
        if (book.getTitle() == null || book.getTitle().isBlank()) {
            int d = name.lastIndexOf('.'); book.setTitle(d > 0 ? name.substring(0, d) : name);
        }
        ReadingProgress p = new ReadingProgress();
        p.setBook(book);
        p.setPercent(0.0);
        p.setStatus("NOT_STARTED");
        p.setLocation("1");
        book.setProgress(p);
        progressRepository.save(p);
        return BookDto.from(bookRepository.save(book));
    }

    @Transactional(readOnly = true)
    public List<BookDto> list() {
        return bookRepository.findAll().stream().map(BookDto::from).sorted(Comparator.comparing(BookDto::createdAt).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public BookDto get(String id) { return BookDto.from(getEntity(id)); }

    @Transactional(readOnly = true)
    public Book getEntity(String id) {
        return bookRepository.findById(id).orElseThrow(() -> new NotFoundException("Livro não encontrado"));
    }

    @Transactional
    public BookDto update(String id, BookUpdateRequest req) {
        Book b = getEntity(id);
        if (req.title() != null && !req.title().isBlank()) b.setTitle(req.title().trim());
        if (req.author() != null) b.setAuthor(req.author().trim());
        if (req.tags() != null) b.setTags(req.tags().trim());
        if (req.description() != null) b.setDescription(req.description());
        return BookDto.from(bookRepository.save(b));
    }

    @Transactional
    public void delete(String id) {
        Book b = getEntity(id);
        storageService.deleteIfExists(b.getStoragePath());
        storageService.deleteIfExists(b.getCoverPath());
        bookRepository.delete(b);
    }

    @Transactional(readOnly = true)
    public Resource getFile(String id) { return storageService.loadAsResource(getEntity(id).getStoragePath()); }
    @Transactional(readOnly = true)
    public Resource getCover(String id) {
        Book b = getEntity(id);
        if (b.getCoverPath() == null) throw new NotFoundException("Capa não encontrada");
        return storageService.loadAsResource(b.getCoverPath());
    }

    @Transactional
    public BookDto.ProgressDto getProgress(String id) {
        Book b = getEntity(id);
        ReadingProgress p = progressRepository.findByBookId(id).orElseGet(() -> {
            ReadingProgress rp = new ReadingProgress(); rp.setBook(b); rp.setStatus("NOT_STARTED"); rp.setPercent(0.0); rp.setLocation("1");
            return progressRepository.save(rp);
        });
        return new BookDto.ProgressDto(p.getLocation(), p.getPercent(), p.getStatus(), p.getUpdatedAt());
    }

    @Transactional
    public BookDto.ProgressDto updateProgress(String id, ProgressRequest req) {
        Book b = getEntity(id);
        ReadingProgress p = progressRepository.findByBookId(id).orElseGet(() -> { ReadingProgress rp = new ReadingProgress(); rp.setBook(b); return rp; });
        if (req.location() != null) p.setLocation(req.location());
        if (req.percent() != null) p.setPercent(Math.max(0, Math.min(100, req.percent())));
        if (req.status() != null) p.setStatus(req.status());
        else if (p.getPercent() != null && p.getPercent() >= 99.5) p.setStatus("FINISHED");
        else p.setStatus("READING");
        p = progressRepository.save(p);
        b.setProgress(p);
        return new BookDto.ProgressDto(p.getLocation(), p.getPercent(), p.getStatus(), p.getUpdatedAt());
    }

    @Transactional
    public Bookmark addBookmark(String id, BookmarkRequest req) {
        Book b = getEntity(id);
        Bookmark bm = new Bookmark();
        bm.setBook(b);
        bm.setLocation(req.location() == null ? "1" : req.location());
        bm.setLabel(req.label());
        return bookmarkRepository.save(bm);
    }
    @Transactional(readOnly = true)
    public List<Bookmark> listBookmarks(String id) { getEntity(id); return bookmarkRepository.findByBookIdOrderByCreatedAtDesc(id); }

    @Transactional
    public Note addNote(String id, NoteRequest req) {
        Book b = getEntity(id);
        if (req.content() == null || req.content().isBlank()) throw new BadRequestException("Conteúdo da nota obrigatório");
        Note n = new Note();
        n.setBook(b);
        n.setLocation(req.location());
        n.setSelectedText(req.selectedText());
        n.setContent(req.content());
        n.setColor(req.color() == null ? "#ffeb3b" : req.color());
        return noteRepository.save(n);
    }
    @Transactional(readOnly = true)
    public List<Note> listNotes(String id) { getEntity(id); return noteRepository.findByBookIdOrderByCreatedAtDesc(id); }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(String id, String q) {
        Book b = getEntity(id);
        if (q == null || q.isBlank()) return List.of();
        String needle = q.toLowerCase();
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            if ("pdf".equalsIgnoreCase(b.getExtension())) {
                try (PDDocument doc = Loader.loadPDF(new File(b.getStoragePath()))) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    int pages = doc.getNumberOfPages();
                    for (int i = 1; i <= pages; i++) {
                        stripper.setStartPage(i); stripper.setEndPage(i);
                        String text = stripper.getText(doc);
                        if (text != null && text.toLowerCase().contains(needle)) {
                            String snippet = snippet(text, needle);
                            results.add(Map.of("page", i, "snippet", snippet));
                            if (results.size() >= 50) break;
                        }
                    }
                }
            } else if ("txt".equalsIgnoreCase(b.getExtension())) {
                String text = Files.readString(Paths.get(b.getStoragePath()), StandardCharsets.UTF_8);
                if (text.toLowerCase().contains(needle)) results.add(Map.of("page", 1, "snippet", snippet(text, needle)));
            }
        } catch (Exception ignored) {}
        return results;
    }

    private String snippet(String text, String needle) {
        String lower = text.toLowerCase();
        int idx = lower.indexOf(needle);
        if (idx < 0) return text.substring(0, Math.min(120, text.length()));
        int start = Math.max(0, idx - 40); int end = Math.min(text.length(), idx + needle.length() + 40);
        return (start>0?"…":"") + text.substring(start, end).replaceAll("\\s+", " ").trim() + (end<text.length()?"…":"");
    }

    private String guessMime(String ext) {
        return switch (ext.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "epub" -> "application/epub+zip";
            case "txt" -> "text/plain";
            case "cbz" -> "application/vnd.comicbook+zip";
            default -> "application/octet-stream";
        };
    }
}
