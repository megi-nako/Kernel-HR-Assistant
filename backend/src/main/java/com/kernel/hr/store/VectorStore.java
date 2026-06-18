package com.kernel.hr.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kernel.hr.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);

    public record Scored(Chunk chunk, double score) {}

    private final Map<String, Chunk> store = new ConcurrentHashMap<>();
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public VectorStore(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        Path indexPath = Paths.get(props.getIndex().getPath());
        if (!Files.exists(indexPath)) {
            log.info("Index file not found at {}; starting with empty store.", indexPath);
            return;
        }
        try {
            List<Chunk> chunks = objectMapper.readValue(indexPath.toFile(), new TypeReference<List<Chunk>>() {});
            store.clear();
            for (Chunk chunk : chunks) {
                store.put(chunk.id(), chunk);
            }
            log.info("Loaded {} chunks from index at {}", store.size(), indexPath);
        } catch (IOException e) {
            log.error("Failed to load index from {}: {}", indexPath, e.getMessage(), e);
        }
    }

    public void upsert(List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            store.put(chunk.id(), chunk);
        }
    }

    public List<Scored> search(float[] queryVector, String office, int k) {
        return store.values().stream()
                .filter(chunk -> office.equals(chunk.office()))
                .filter(chunk -> chunk.vector() != null)
                .map(chunk -> new Scored(chunk, cosine(queryVector, chunk.vector())))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(k)
                .collect(Collectors.toList());
    }

    public void save() {
        Path indexPath = Paths.get(props.getIndex().getPath());
        try {
            Files.createDirectories(indexPath.getParent());
            Path tmpPath = indexPath.resolveSibling(indexPath.getFileName() + ".tmp");
            List<Chunk> chunks = new ArrayList<>(store.values());
            objectMapper.writeValue(tmpPath.toFile(), chunks);
            Files.move(tmpPath, indexPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Saved {} chunks to index at {}", chunks.size(), indexPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save index to " + indexPath, e);
        }
    }

    public boolean hasChunk(String id) {
        return store.containsKey(id);
    }

    public int countByOffice(String office) {
        return (int) store.values().stream()
                .filter(chunk -> office.equals(chunk.office()))
                .count();
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (normA * normB);
    }
}
