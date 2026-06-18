package com.kernel.hr.embedding;

import java.util.List;

// Contract A — frozen at P0. Megi implements VoyageEmbeddingClient and DjlEmbeddingClient.
public interface EmbeddingClient {

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
