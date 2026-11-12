package com.bibliotheca.service;

import com.bibliotheca.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Service
public class StorageService {
    @Value("${bibliotheca.storage.path:./data/books}")
    private String storagePath;
    @Value("${bibliotheca.upload.max-size-bytes:209715200}")
    private long maxSize;
    @Value("${bibliotheca.upload.allowed-ext:pdf,epub,mobi,cbz,txt,fb2,azw3}")
    private String allowedExts;

    private Path root;
    private Set<String> allowed;

    @PostConstruct
    public void init() throws IOException {
        root = Paths.get(storagePath).toAbsolutePath().normalize();
        Files.createDirectories(root);
        allowed = Set.of(allowedExts.toLowerCase().split(","));
    }

    public StoredFile store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new BadRequestException("Arquivo vazio");
        if (file.getSize() > maxSize) throw new BadRequestException("Arquivo excede o tamanho máximo permitido");
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        String ext = getExtension(original);
        if (!allowed.contains(ext.toLowerCase())) throw new BadRequestException("Extensão não permitida: " + ext);
        String id = UUID.randomUUID().toString();
        String filename = id + "." + ext.toLowerCase();
        Path target = root.resolve(filename);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredFile(id, target.toString(), ext.toLowerCase(), original, file.getSize(), file.getContentType());
    }

    public Path storeCover(String bookId, byte[] pngBytes) throws IOException {
        Path p = root.resolve(bookId + "-cover.png");
        Files.write(p, pngBytes);
        return p;
    }

    public Resource loadAsResource(String pathStr) {
        Path p = Paths.get(pathStr);
        if (!Files.exists(p)) throw new com.bibliotheca.exception.NotFoundException("Arquivo não encontrado");
        return new FileSystemResource(p);
    }

    public void deleteIfExists(String pathStr) {
        if (pathStr == null) return;
        try { Files.deleteIfExists(Paths.get(pathStr)); } catch (IOException ignored) {}
    }

    public Path getRoot() { return root; }

    public static String getExtension(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1) : "";
    }

    public record StoredFile(String uuid, String path, String ext, String originalName, long size, String mimeType) {}
}
