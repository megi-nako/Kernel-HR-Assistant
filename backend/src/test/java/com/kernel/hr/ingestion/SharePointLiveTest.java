package com.kernel.hr.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kernel.hr.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LIVE SharePoint read test. Hits Microsoft Graph for real, so it only runs when the four
 * credentials are present in the environment — otherwise JUnit skips it (no failure on CI /
 * machines without creds).
 *
 * Run it in isolation (no Postgres / no Voyage needed):
 *
 *   GRAPH_TENANT_ID=...  GRAPH_CLIENT_ID=...  GRAPH_CLIENT_SECRET=...  SHAREPOINT_DRIVE_ID=... \
 *   mvn -Dtest=SharePointLiveTest test
 *
 * On Windows PowerShell:
 *   $env:GRAPH_TENANT_ID="..."; $env:GRAPH_CLIENT_ID="..."; $env:GRAPH_CLIENT_SECRET="...";
 *   $env:SHAREPOINT_DRIVE_ID="..."; mvn -Dtest=SharePointLiveTest test
 */
@EnabledIfEnvironmentVariable(named = "GRAPH_TENANT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GRAPH_CLIENT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GRAPH_CLIENT_SECRET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SHAREPOINT_DRIVE_ID", matches = ".+")
class SharePointLiveTest {

    @Test
    void readsAlbaniaDocumentsFromSharePoint() throws Exception {
        AppProperties props = new AppProperties();
        props.getGraph().setTenantId(System.getenv("GRAPH_TENANT_ID"));
        props.getGraph().setClientId(System.getenv("GRAPH_CLIENT_ID"));
        props.getGraph().setClientSecret(System.getenv("GRAPH_CLIENT_SECRET"));
        props.getSharepoint().setDriveId(System.getenv("SHAREPOINT_DRIVE_ID"));

        SharePointClient client = new SharePointClient(props, new ObjectMapper());

        List<SourceDoc> docs = client.loadAll();

        System.out.printf("[SharePoint] downloaded %d document(s)%n", docs.size());
        docs.forEach(d -> System.out.printf("  - %s (%s, %d bytes, modified %s)%n",
                d.name(), d.fileType(), d.bytes().length, d.lastModified()));

        assertFalse(docs.isEmpty(), "SharePoint drive should expose at least one PDF/PPTX");
        assertTrue(docs.stream().allMatch(d -> d.office().equals("albania")),
                "every SharePoint doc must be tagged office=albania");
        assertTrue(docs.stream().allMatch(d -> d.bytes().length > 0), "downloaded bytes must be non-empty");

        // Confirm the bytes are real parseable documents, not an error page.
        DocumentLoader loader = new DocumentLoader();
        boolean anyText = docs.stream().map(loader::parse).anyMatch(p -> !p.text().isBlank());
        assertTrue(anyText, "Tika should extract text from at least one downloaded document");
    }
}
