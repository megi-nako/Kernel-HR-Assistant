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
 * Reads HR documents directly from a plain folder on disk (the "localFolder" backend
 * in the SourceService abstraction) — no ZIP extraction, no SharePoint.
 *
 * Walks the folder recursively, keeps .pdf and .pptx files, and tags every document
 * with the supplied office — never inferred from filename or language.
 */
@Component
public class LocalFolderSource {

    private static final Logger log = LoggerFactory.getLogger(LocalFolderSource.class);

    public List<SourceDoc> loadAll(String folderPathStr, String office) {
        if (folderPathStr == null || folderPathStr.isBlank()) {
            throw new IllegalStateException(
                    "Folder path is not set for office '" + office + "'. "
                            + "Set the corresponding *_FOLDER_PATH env var.");
        }

        Path folder = Paths.get(folderPathStr).toAbsolutePath();
        if (!Files.exists(folder)) {
            throw new IllegalStateException("Source folder not found at: " + folder);
        }
        if (!Files.isDirectory(folder)) {
            throw new IllegalStateException("Source folder path is not a directory: " + folder);
        }

        List<SourceDoc> docs = new ArrayList<>();
        try (var stream = Files.walk(folder)) {
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
                            log.debug("Loaded document from folder: {}", name);
                        } catch (IOException e) {
                            log.warn("Failed to read file {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source folder " + folder, e);
        }

        log.info("Loaded {} documents from folder {} (office={})", docs.size(), folder, office);
        return docs;
    }
}
