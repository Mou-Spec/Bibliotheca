package com.bibliotheca.service;

import com.bibliotheca.entity.Book;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class MetadataService {
    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);
    private final StorageService storageService;

    public MetadataService(StorageService storageService) { this.storageService = storageService; }

    public void enrichAndExtractCover(Book book, File file) {
        String ext = book.getExtension();
        try {
            if ("pdf".equalsIgnoreCase(ext)) {
                enrichPdf(book, file);
            } else if ("epub".equalsIgnoreCase(ext)) {
                enrichEpub(book, file);
            } else {
                defaultMeta(book);
            }
        } catch (Exception e) {
            log.warn("Metadata extraction failed for {}: {}", book.getOriginalFilename(), e.getMessage());
            defaultMeta(book);
        }
    }

    private void enrichPdf(Book book, File file) throws Exception {
        try (PDDocument doc = Loader.loadPDF(file)) {
            var info = doc.getDocumentInformation();
            String title = safe(info.getTitle());
            String author = safe(info.getAuthor());
            if (title != null) book.setTitle(title);
            if (author != null) book.setAuthor(author);
            book.setTotalPages(doc.getNumberOfPages());
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage img = renderer.renderImageWithDPI(0, 96);
            if (img != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                Path p = storageService.storeCover(book.getId(), baos.toByteArray());
                book.setCoverPath(p.toString());
            }
        }
    }

    private void enrichEpub(Book book, File file) {
        defaultMeta(book);
        try (ZipFile zip = new ZipFile(file)) {
            // try common cover names
            String[] candidates = {"cover.jpg","cover.jpeg","cover.png","Images/cover.jpg","images/cover.jpg","OEBPS/images/cover.jpg","OPS/images/cover.jpg"};
            for (String c : candidates) {
                ZipEntry e = zip.getEntry(c);
                if (e != null) {
                    try (InputStream in = zip.getInputStream(e)) {
                        byte[] data = in.readAllBytes();
                        Path p = storageService.storeCover(book.getId(), data);
                        book.setCoverPath(p.toString());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void defaultMeta(Book book) {
        if (book.getTitle() == null || book.getTitle().isBlank()) {
            String name = book.getOriginalFilename();
            if (name == null) name = "Sem título";
            int dot = name.lastIndexOf('.');
            book.setTitle(dot > 0 ? name.substring(0, dot) : name);
        }
    }

    private String safe(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
