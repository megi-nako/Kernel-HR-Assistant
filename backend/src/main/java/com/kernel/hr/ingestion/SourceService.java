package com.kernel.hr.ingestion;

import com.kernel.hr.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Routes document loading to the correct source per office and ALB_SOURCE config.
 *
 * Serbia  → always ZIP (SRB_ZIP_PATH)
 * Albania → "sharepoint" (default): Graph API via SharePointClient
 *           "local"                : local ZIP via LocalZipAlbaniaSource (ALB_ZIP_PATH)
 */
@Service
public class SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceService.class);

    private final AppProperties props;
    private final ZipSource zipSource;
    private final SharePointClient sharePointClient;
    private final LocalZipAlbaniaSource localZipAlbaniaSource;

    public SourceService(AppProperties props,
                         ZipSource zipSource,
                         SharePointClient sharePointClient,
                         LocalZipAlbaniaSource localZipAlbaniaSource) {
        this.props = props;
        this.zipSource = zipSource;
        this.sharePointClient = sharePointClient;
        this.localZipAlbaniaSource = localZipAlbaniaSource;
    }

    public List<SourceDoc> iterDocuments(String office) {
        return switch (office) {
            case "serbia" -> zipSource.loadAll();
            case "albania" -> loadAlbania();
            default -> throw new IllegalArgumentException("Unknown office: " + office);
        };
    }

    private List<SourceDoc> loadAlbania() {
        String source = props.getAlb().getSource();
        log.info("Loading Albania documents via source: {}", source);
        return switch (source) {
            case "sharepoint" -> {
                try {
                    yield sharePointClient.loadAll();
                } catch (Exception e) {
                    throw new RuntimeException("SharePoint ingestion failed: " + e.getMessage(), e);
                }
            }
            case "local" -> localZipAlbaniaSource.loadAll();
            default -> throw new IllegalStateException(
                    "Unknown ALB_SOURCE: '" + source + "'. Must be 'sharepoint' or 'local'.");
        };
    }
}
