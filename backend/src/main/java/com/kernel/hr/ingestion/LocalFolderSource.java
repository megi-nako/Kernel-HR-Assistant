package com.kernel.hr.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads HR documents directly from a local folder (no ZIP extraction needed).
 * Walks the directory recursively and picks up every .pdf and .pptx file.
 * Office is always supplied by the caller — never inferred from file content.
 */
@Component
public class LocalFolderSource {

    private static final Logger log = LoggerFactory.getLogger(LocalFolderSource.class);

    /**
     * @param folderPath absolute or relative path to the folder containing documents
     * @param office     "serbia" | "albania" — must come from the caller, never from filenames
     */
    public List<SourceDoc> loadAll(String folderPath, String office) {
        if (folderPath == null || folderPath.isBlank()) {
            throw new IllegalStateException(
                    "Folder path is not configured for office '" + office + "'. " +
                    "Set SRB_FOLDER_PATH or ALB_FOLDER_PATH.");
        }

        Path dir = Paths.get(folderPath).toAbsolutePath();
        if (!Files.exists(dir)) {
            throw new IllegalStateException(
                    "Document folder not found for office '" + office + "': " + dir);
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException(
                    "Path is not a directory for office '" + office + "': " + dir);
        }

        List<SourceDoc> docs = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".pdf") || name.endsWith(".pptx");
                    })
                    .forEach(p -> {
                        try {
                            byte[] bytes = Files.readAllBytes(p);
                            String name = p.getFileName().toString();
                            String sourceUrl = p.toAbsolutePath().toString();
                            String lastModified = Files.getLastModifiedTime(p).toInstant().toString();
                            String fileType = name.toLowerCase().endsWith(".pdf") ? "pdf" : "pptx";
                            docs.add(new SourceDoc(bytes, name, sourceUrl, lastModified, office, fileType));
                            log.debug("Loaded from folder: {}", name);
                        } catch (IOException e) {
                            log.warn("Failed to read file {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk folder " + dir, e);
        }

        log.info("Loaded {} documents from folder {} (office={})", docs.size(), dir, office);
        return docs;
    }
}
