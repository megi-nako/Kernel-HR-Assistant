package com.kernel.hr.ingestion;

import com.kernel.hr.embedding.EmbeddingClient;
import com.kernel.hr.store.Chunk;
import com.kernel.hr.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class Indexer {

    private static final Logger log = LoggerFactory.getLogger(Indexer.class);

    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final DocumentLoader documentLoader;

    public Indexer(EmbeddingClient embeddingClient, VectorStore vectorStore, DocumentLoader documentLoader) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.documentLoader = documentLoader;
    }

    public void index(List<SourceDoc> docs) {
        for (SourceDoc src : docs) {
            DocumentLoader.ParsedDoc parsed = documentLoader.parse(src);
            if (parsed.text().isBlank()) {
                log.debug("Skipping blank document: {}", src.name());
                continue;
            }

            List<String> textChunks = chunkText(parsed.text(), 800, 100);
            List<String> textsToEmbed = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();

            for (int i = 0; i < textChunks.size(); i++) {
                String chunkId = src.name() + ":" + src.lastModified() + ":" + i;
                // skip if already indexed (idempotent)
                if (vectorStore.hasChunk(chunkId)) {
                    log.debug("Chunk already indexed, skipping: {}", chunkId);
                    continue;
                }
                textsToEmbed.add(textChunks.get(i));
                indices.add(i);
            }

            if (textsToEmbed.isEmpty()) {
                log.debug("All chunks already indexed for: {}", src.name());
                continue;
            }

            log.info("Embedding {} chunks for document: {}", textsToEmbed.size(), src.name());
            List<float[]> vectors = embeddingClient.embedAll(textsToEmbed);
            List<Chunk> newChunks = new ArrayList<>();
            for (int j = 0; j < textsToEmbed.size(); j++) {
                int i = indices.get(j);
                String chunkId = src.name() + ":" + src.lastModified() + ":" + i;
                newChunks.add(new Chunk(
                        chunkId,
                        textChunks.get(i),
                        src.office(),
                        src.name(),
                        src.sourceUrl(),
                        src.lastModified(),
                        null,
                        src.fileType(),
                        parsed.language(),
                        vectors.get(j)
                ));
            }
            vectorStore.upsert(newChunks);
            log.info("Indexed {} new chunks for document: {}", newChunks.size(), src.name());
        }
        vectorStore.save();
    }

    private List<String> chunkText(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
            start += size - overlap;
        }
        return chunks;
    }
}
