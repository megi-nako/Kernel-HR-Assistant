package com.kernel.hr.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kernel.hr.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@Primary
public class VoyageEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(VoyageEmbeddingClient.class);
    private static final String VOYAGE_ENDPOINT = "https://api.voyageai.com/v1/embeddings";

    // Free tier: 3 RPM, 10K TPM. Batch size 8 keeps each request well under TPM.
    // A 21-second pause between batches keeps us at ~2.8 RPM — safely under the limit.
    private static final int BATCH_SIZE = 8;
    private static final long BATCH_DELAY_MS = 21_000;
    private static final int MAX_RETRIES = 6;
    private static final long INITIAL_BACKOFF_MS = 22_000;

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public VoyageEmbeddingClient(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        String apiKey = props.getEmbedding().getVoyageApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("VOYAGE_API_KEY not configured");
        }

        List<float[]> allVectors = new ArrayList<>();
        int totalBatches = (int) Math.ceil((double) texts.size() / BATCH_SIZE);

        for (int offset = 0; offset < texts.size(); offset += BATCH_SIZE) {
            int batchNum = offset / BATCH_SIZE + 1;
            List<String> batch = texts.subList(offset, Math.min(offset + BATCH_SIZE, texts.size()));

            log.info("Embedding batch {}/{} ({} texts)...", batchNum, totalBatches, batch.size());
            List<float[]> batchVectors = embedWithRetry(batch, apiKey);
            allVectors.addAll(batchVectors);

            // Pause between batches to respect the 3 RPM free-tier rate limit.
            if (offset + BATCH_SIZE < texts.size()) {
                log.info("Rate-limit pause {}s before next batch...", BATCH_DELAY_MS / 1000);
                sleep(BATCH_DELAY_MS);
            }
        }
        return allVectors;
    }

    private List<float[]> embedWithRetry(List<String> texts, String apiKey) {
        long backoff = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return embedBatch(texts, apiKey);
            } catch (RateLimitException e) {
                if (attempt == MAX_RETRIES) throw new RuntimeException("Voyage rate limit exceeded after " + MAX_RETRIES + " retries", e);
                log.warn("Voyage rate limit (429) — waiting {}s before retry {}/{}...",
                        backoff / 1000, attempt, MAX_RETRIES);
                sleep(backoff);
                backoff = Math.min(backoff * 2, 120_000);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private List<float[]> embedBatch(List<String> texts, String apiKey) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", props.getEmbedding().getModel());
            ArrayNode inputArray = body.putArray("input");
            for (String text : texts) inputArray.add(text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VOYAGE_ENDPOINT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) throw new RateLimitException(response.body());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Voyage API error " + response.statusCode() + ": " + response.body());
            }

            JsonNode data = objectMapper.readTree(response.body()).get("data");
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode arr = item.get("embedding");
                float[] vec = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) vec[i] = (float) arr.get(i).asDouble();
                vectors.add(vec);
            }
            return vectors;

        } catch (RateLimitException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling Voyage API", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Voyage embedding API", e);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static class RateLimitException extends RuntimeException {
        RateLimitException(String body) { super("429: " + body); }
    }
}
