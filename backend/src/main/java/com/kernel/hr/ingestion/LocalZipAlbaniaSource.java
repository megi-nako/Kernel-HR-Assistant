package com.kernel.hr.ingestion;

import com.kernel.hr.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Loads Albania HR documents from a local ZIP file (ALB_SOURCE=local fallback).
 * Every document is tagged office="albania" from the source — never from filename or language.
 */
@Component
public class LocalZipAlbaniaSource {

    private static final Logger log = LoggerFactory.getLogger(LocalZipAlbaniaSource.class);
    private static final String EXTRACT_DIR_NAME = "_alb_extracted";

    private final AppProperties props;

    public LocalZipAlbaniaSource(AppProperties props) {
        this.props = props;
    }

    public List<SourceDoc> loadAll() {
        String zipPathStr = props.getAlb().getZipPath();
        if (zipPathStr == null || zipPathStr.isBlank()) {
            throw new IllegalStateException(
                    "ALB_ZIP_PATH is not set. Set it to the Albania HR documents ZIP file path.");
        }

        Path zipPath = Paths.get(zipPathStr).toAbsolutePath();
        if (!Files.exists(zipPath)) {
            throw new IllegalStateException("Albania ZIP file not found at: " + zipPath);
        }

        Path extractDir = zipPath.getParent().resolve(EXTRACT_DIR_NAME);

        if (!Files.exists(extractDir) || !isDirectoryNonEmpty(extractDir)) {
            log.info("Extracting Albania ZIP {} to {}", zipPath, extractDir);
            extractZip(zipPath, extractDir);
        } else {
            log.info("Albania extraction directory already populated at {}; skipping.", extractDir);
        }

        List<SourceDoc> docs = new ArrayList<>();
        try (var stream = Files.walk(extractDir)) {
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
                            docs.add(new SourceDoc(bytes, name, sourceUrl, lastModified, "albania", fileType));
                            log.debug("Loaded Albania document: {}", name);
                        } catch (IOException e) {
                            log.warn("Failed to read Albania file {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk Albania extraction directory " + extractDir, e);
        }

        log.info("Loaded {} Albania documents from local ZIP", docs.size());
        return docs;
    }

    private boolean isDirectoryNonEmpty(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.findFirst().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    private void extractZip(Path zipPath, Path extractDir) {
        try {
            Files.createDirectories(extractDir);
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        Path outPath = extractDir.resolve(entry.getName());
                        Files.createDirectories(outPath.getParent());
                        Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            log.info("Albania ZIP extraction complete to {}", extractDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract Albania ZIP " + zipPath + " to " + extractDir, e);
        }
    }
}
