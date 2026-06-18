package com.kernel.hr.ingestion;

import com.kernel.hr.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Routes document loading to the correct source per office.
 *
 * Serbia  — SRB_SOURCE : zip (default) | folder
 * Albania — ALB_SOURCE : sharepoint (default) | local | folder
 *
 * "zip"        → ZipSource            (extracts ZIP, tags office from source)
 * "folder"     → LocalFolderSource    (walks a directory, tags office from source)
 * "local"      → LocalZipAlbaniaSource (Albania-specific ZIP fallback)
 * "sharepoint" → SharePointClient     (Graph API download)
 */
@Service
public class SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceService.class);

    private final AppProperties props;
    private final ZipSource zipSource;
    private final LocalFolderSource localFolderSource;
    private final LocalZipAlbaniaSource localZipAlbaniaSource;
    private final SharePointClient sharePointClient;

    public SourceService(AppProperties props,
                         ZipSource zipSource,
                         LocalFolderSource localFolderSource,
                         LocalZipAlbaniaSource localZipAlbaniaSource,
                         SharePointClient sharePointClient) {
        this.props = props;
        this.zipSource = zipSource;
        this.localFolderSource = localFolderSource;
        this.localZipAlbaniaSource = localZipAlbaniaSource;
        this.sharePointClient = sharePointClient;
    }

    public List<SourceDoc> iterDocuments(String office) {
        return switch (office) {
            case "serbia"  -> loadSerbia();
            case "albania" -> loadAlbania();
            default -> throw new IllegalArgumentException("Unknown office: " + office);
        };
    }

    private List<SourceDoc> loadSerbia() {
        String source = props.getSrb().getSource();
        log.info("Loading Serbia documents via source: {}", source);
        return switch (source) {
            case "zip"    -> zipSource.loadAll();
            case "folder" -> localFolderSource.loadAll(props.getSrb().getFolderPath(), "serbia");
            default -> throw new IllegalStateException(
                    "Unknown SRB_SOURCE: '" + source + "'. Must be 'zip' or 'folder'.");
        };
    }

    private List<SourceDoc> loadAlbania() {
        String source = props.getAlb().getSource();
        log.info("Loading Albania documents via source: {}", source);
        return switch (source) {
            case "sharepoint" -> {
                try { yield sharePointClient.loadAll(); }
                catch (Exception e) { throw new RuntimeException("SharePoint ingestion failed: " + e.getMessage(), e); }
            }
            case "local"  -> localZipAlbaniaSource.loadAll();
            case "folder" -> localFolderSource.loadAll(props.getAlb().getFolderPath(), "albania");
            default -> throw new IllegalStateException(
                    "Unknown ALB_SOURCE: '" + source + "'. Must be 'sharepoint', 'local', or 'folder'.");
        };
    }
}
