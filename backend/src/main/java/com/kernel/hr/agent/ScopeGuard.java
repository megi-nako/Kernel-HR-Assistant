package com.kernel.hr.agent;

import com.kernel.hr.retrieval.RetrieverService;
import com.kernel.hr.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// Grounding gate (#2): refuses off-topic queries before calling the LLM.
// Primary mechanism: if no retrieved chunk scores above threshold, refuse.
// Bypassed when the index is empty (not yet built) to avoid blocking development.
@Component
public class ScopeGuard {

    private static final Logger log = LoggerFactory.getLogger(ScopeGuard.class);

    private final RetrieverService retrieverService;
    private final VectorStore vectorStore;
    private final double threshold;

    public ScopeGuard(RetrieverService retrieverService,
                      VectorStore vectorStore,
                      @Value("${kernel.agent.scope-threshold:0.35}") double threshold) {
        this.retrieverService = retrieverService;
        this.vectorStore = vectorStore;
        this.threshold = threshold;
    }

    /**
     * Returns Optional.empty() if the query is in scope (proceed to agent).
     * Returns Optional.of(refusalMessage) if out of scope (refuse without calling LLM).
     */
    public Optional<String> check(String query, String office) {
        int docCount = vectorStore.countByOffice(office);
        if (docCount == 0) {
            // Index not built yet — bypass the gate to allow development/testing.
            log.warn("ScopeGuard bypassed: index is empty for office={}. Build the index with --ingest.", office);
            return Optional.empty();
        }

        List<RetrieverService.RetrievedChunk> results = retrieverService.retrieve(query, office, 1);
        if (results.isEmpty() || results.get(0).score() < threshold) {
            String refusal = "I can only help with HR questions for the " + office +
                             " office, based on our HR documents.";
            log.debug("ScopeGuard refused query for office={} (score={}): {}",
                office, results.isEmpty() ? "n/a" : results.get(0).score(), query);
            return Optional.of(refusal);
        }
        return Optional.empty();
    }
}
