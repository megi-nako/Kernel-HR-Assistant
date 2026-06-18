package com.kernel.hr.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

// Phase 0 stub — registered as a @Bean in EmbeddingClientConfig only when no other
// EmbeddingClient bean exists. Megi's VoyageEmbeddingClient/DjlEmbeddingClient
// (feat/ingestion-megi) will take over once wired.
public class StubEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(StubEmbeddingClient.class);
    static final int DIMENSION = 1024; // matches voyage-3

    @Override
    public float[] embed(String text) {
        log.warn("StubEmbeddingClient: returning zero vector. Set EMBEDDING_PROVIDER=voyage (P1).");
        return new float[DIMENSION];
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        log.warn("StubEmbeddingClient: returning zero vectors for {} texts.", texts.size());
        return texts.stream().map(t -> new float[DIMENSION]).collect(Collectors.toList());
    }
}
