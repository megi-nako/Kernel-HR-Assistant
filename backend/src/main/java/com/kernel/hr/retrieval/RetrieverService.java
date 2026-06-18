package com.kernel.hr.retrieval;

import com.kernel.hr.store.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrieverService {

    // Contract B — frozen at P0.
    public record RetrievedChunk(
        String content,
        String sourceName,
        String sourceUrl,
        String lastModified,
        Integer page,
        double score,
        String office
    ) {}

    private final VectorStore vectorStore;
    private final com.kernel.hr.embedding.EmbeddingClient embeddingClient;

    public RetrieverService(VectorStore vectorStore,
                            com.kernel.hr.embedding.EmbeddingClient embeddingClient) {
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
    }

    /**
     * Retrieve top-k chunks for the given query, filtered to the given office.
     * office is REQUIRED — there is no "all offices" path for end-user queries.
     */
    public List<RetrievedChunk> retrieve(String query, String office, int k) {
        float[] queryVector = embeddingClient.embed(query);
        return vectorStore.search(queryVector, office, k).stream()
            .map(s -> new RetrievedChunk(
                s.chunk().content(),
                s.chunk().sourceName(),
                s.chunk().sourceUrl(),
                s.chunk().lastModified(),
                s.chunk().page(),
                s.score(),
                s.chunk().office()
            ))
            .toList();
    }
}
