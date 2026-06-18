package com.kernel.hr.ingestion;

import com.kernel.hr.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

    private final SourceService sourceService;
    private final Indexer indexer;
    private final VectorStore vectorStore;

    public IngestionRunner(SourceService sourceService, Indexer indexer, VectorStore vectorStore) {
        this.sourceService = sourceService;
        this.indexer = indexer;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("ingest")) {
            vectorStore.load();
            return;
        }

        List<String> values = args.getOptionValues("ingest");
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("--ingest requires a value: serbia|albania|all");
        }
        String target = values.get(0);

        List<String> offices = switch (target) {
            case "serbia" -> List.of("serbia");
            case "albania" -> List.of("albania");
            case "all" -> List.of("serbia", "albania");
            default -> throw new IllegalArgumentException("--ingest must be serbia|albania|all, got: " + target);
        };

        for (String office : offices) {
            log.info("Starting ingestion for office: {}", office);
            List<SourceDoc> docs = sourceService.iterDocuments(office);
            log.info("Found {} documents for office: {}", docs.size(), office);
            indexer.index(docs);
            log.info("Ingestion complete for {}. Chunks: {}", office, vectorStore.countByOffice(office));
        }
    }
}
