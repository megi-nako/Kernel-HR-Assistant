package com.kernel.hr.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kernel.hr.config.AppProperties;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Downloads Albania HR documents from SharePoint via Microsoft Graph REST API.
 * Uses MSAL4J client-credentials flow for authentication.
 *
 * Required env vars: GRAPH_TENANT_ID, GRAPH_CLIENT_ID, GRAPH_CLIENT_SECRET,
 *                    SHAREPOINT_DRIVE_ID
 */
@Component
public class SharePointClient {

    private static final Logger log = LoggerFactory.getLogger(SharePointClient.class);
    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";
    private static final Set<String> SCOPE = Collections.singleton("https://graph.microsoft.com/.default");

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public SharePointClient(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public List<SourceDoc> loadAll() throws Exception {
        validateConfig();
        String token = acquireToken();
        String driveId = props.getSharepoint().getDriveId();

        List<SourceDoc> docs = new ArrayList<>();
        listItemsRecursive(token, driveId, "root", docs);
        log.info("Downloaded {} documents from SharePoint (Albania)", docs.size());
        return docs;
    }

    private void listItemsRecursive(String token, String driveId, String itemId, List<SourceDoc> docs) throws Exception {
        String url = GRAPH_BASE + "/drives/" + driveId + "/items/" + itemId + "/children"
                + "?$select=id,name,file,folder,lastModifiedDateTime&$top=200";

        while (url != null) {
            String body = graphGet(token, url);
            JsonNode root = objectMapper.readTree(body);
            JsonNode values = root.path("value");

            for (JsonNode item : values) {
                String name = item.path("name").asText();
                String id = item.path("id").asText();
                String lastModified = item.path("lastModifiedDateTime").asText();

                if (item.has("folder")) {
                    // Recurse into sub-folders
                    listItemsRecursive(token, driveId, id, docs);
                } else if (item.has("file")) {
                    String lower = name.toLowerCase();
                    if (lower.endsWith(".pdf") || lower.endsWith(".pptx")) {
                        try {
                            byte[] bytes = downloadItem(token, driveId, id);
                            String fileType = lower.endsWith(".pdf") ? "pdf" : "pptx";
                            String sourceUrl = GRAPH_BASE + "/drives/" + driveId + "/items/" + id;
                            docs.add(new SourceDoc(bytes, name, sourceUrl, lastModified, "albania", fileType));
                            log.debug("Downloaded: {}", name);
                        } catch (Exception e) {
                            log.warn("Failed to download {}: {}", name, e.getMessage());
                        }
                    }
                }
            }

            // Handle pagination
            JsonNode nextLink = root.path("@odata.nextLink");
            url = nextLink.isMissingNode() ? null : nextLink.asText();
        }
    }

    private String graphGet(String token, String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Graph API error " + response.statusCode() + " for " + url + ": " + response.body());
        }
        return response.body();
    }

    private byte[] downloadItem(String token, String driveId, String itemId) throws IOException, InterruptedException {
        String url = GRAPH_BASE + "/drives/" + driveId + "/items/" + itemId + "/content";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        // Graph redirects to a download URL — HttpClient follows redirects by default
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Download failed " + response.statusCode() + " for item " + itemId);
        }
        return response.body();
    }

    private String acquireToken() throws Exception {
        AppProperties.Graph g = props.getGraph();
        ConfidentialClientApplication app = ConfidentialClientApplication
                .builder(g.getClientId(), ClientCredentialFactory.createFromSecret(g.getClientSecret()))
                .authority("https://login.microsoftonline.com/" + g.getTenantId())
                .build();
        ClientCredentialParameters params = ClientCredentialParameters.builder(SCOPE).build();
        IAuthenticationResult result = app.acquireToken(params).get();
        return result.accessToken();
    }

    private void validateConfig() {
        AppProperties.Graph g = props.getGraph();
        AppProperties.Sharepoint sp = props.getSharepoint();
        if (g.getTenantId().isBlank() || g.getClientId().isBlank() || g.getClientSecret().isBlank()) {
            throw new IllegalStateException(
                    "SharePoint requires GRAPH_TENANT_ID, GRAPH_CLIENT_ID, GRAPH_CLIENT_SECRET env vars");
        }
        if (sp.getDriveId().isBlank()) {
            throw new IllegalStateException("SharePoint requires SHAREPOINT_DRIVE_ID env var");
        }
    }
}
