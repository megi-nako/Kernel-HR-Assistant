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
    private static final String MODEL = "voyage-3";
    private static final int BATCH_SIZE = 128;

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
        List<float[]> results = embedAll(List.of(text));
        return results.get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        String apiKey = props.getEmbedding().getVoyageApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("VOYAGE_API_KEY not configured");
        }

        List<float[]> allVectors = new ArrayList<>();
        for (int offset = 0; offset < texts.size(); offset += BATCH_SIZE) {
            int end = Math.min(offset + BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(offset, end);
            List<float[]> batchVectors = embedBatch(batch, apiKey);
            allVectors.addAll(batchVectors);
        }
        return allVectors;
    }

    private List<float[]> embedBatch(List<String> texts, String apiKey) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", MODEL);
            ArrayNode inputArray = body.putArray("input");
            for (String text : texts) {
                inputArray.add(text);
            }
            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VOYAGE_ENDPOINT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Voyage API error " + response.statusCode() + ": " + response.body());
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode dataArray = responseJson.get("data");

            List<float[]> vectors = new ArrayList<>();
            for (JsonNode dataItem : dataArray) {
                JsonNode embeddingArray = dataItem.get("embedding");
                float[] vector = new float[embeddingArray.size()];
                for (int i = 0; i < embeddingArray.size(); i++) {
                    vector[i] = (float) embeddingArray.get(i).asDouble();
                }
                vectors.add(vector);
            }
            log.debug("Embedded {} texts via Voyage AI", texts.size());
            return vectors;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling Voyage API", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Voyage embedding API", e);
        }
    }
}
