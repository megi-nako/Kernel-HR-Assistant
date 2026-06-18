package com.kernel.hr.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingClientConfig {

    // Active only when no other EmbeddingClient bean exists (e.g., VoyageEmbeddingClient or DjlEmbeddingClient).
    // Megi: add @Primary on VoyageEmbeddingClient / DjlEmbeddingClient to override this.
    @Bean
    @ConditionalOnMissingBean(EmbeddingClient.class)
    public EmbeddingClient stubEmbeddingClient() {
        return new StubEmbeddingClient();
    }
}
