package com.kernel.hr.store;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Contract A stub — Megi implements in feat/ingestion-megi.
@Component
public class VectorStore {

    public record Scored(Chunk chunk, double score) {}

    public void upsert(List<Chunk> chunks) {
        throw new UnsupportedOperationException("VectorStore not yet implemented — see feat/ingestion-megi");
    }

    public List<Scored> search(float[] queryVector, String office, int k) {
        // Stub: returns empty list so retriever tests can run without a real index.
        return new ArrayList<>();
    }

    public void save() {
        throw new UnsupportedOperationException("VectorStore not yet implemented — see feat/ingestion-megi");
    }

    public void load() {
        // No-op stub — real impl loads data/index.json on boot.
    }

    public int countByOffice(String office) {
        return 0;
    }
}
