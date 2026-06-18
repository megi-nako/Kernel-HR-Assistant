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

@Component
public class ZipSource {

    private static final Logger log = LoggerFactory.getLogger(ZipSource.class);

    private final AppProperties props;

    public ZipSource(AppProperties props) {
        this.props = props;
    }

    public List<SourceDoc> loadAll() {
        Path zipPath = Paths.get(props.getSrb().getZipPath()).toAbsolutePath();
        Path extractDir = Paths.get(props.getSrb().getExtractDir()).toAbsolutePath();

        if (!Files.exists(zipPath)) {
            throw new IllegalStateException("Serbia ZIP file not found at: " + zipPath);
        }

        // Extract if not already done (check if extractDir is non-empty)
        if (!Files.exists(extractDir) || !isDirectoryNonEmpty(extractDir)) {
            log.info("Extracting ZIP {} to {}", zipPath, extractDir);
            extractZip(zipPath, extractDir);
        } else {
            log.info("Extraction directory already populated at {}; skipping extraction.", extractDir);
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

                            docs.add(new SourceDoc(bytes, name, sourceUrl, lastModified, "serbia", fileType));
                            log.debug("Loaded document: {}", name);
                        } catch (IOException e) {
                            log.warn("Failed to read file {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk extracted directory " + extractDir, e);
        }

        log.info("Loaded {} documents from Serbia ZIP", docs.size());
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
            log.info("ZIP extraction complete to {}", extractDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract ZIP " + zipPath + " to " + extractDir, e);
        }
    }
}
