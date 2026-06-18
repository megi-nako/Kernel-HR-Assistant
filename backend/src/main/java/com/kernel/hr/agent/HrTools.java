package com.kernel.hr.agent;

import com.kernel.hr.retrieval.RetrieverService;
import com.kernel.hr.retrieval.RetrieverService.RetrievedChunk;
import com.kernel.hr.web.dto.Citation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

// Holds tool definitions for the Anthropic Messages API and executes tool calls.
// office is bound per-call — it is NEVER a tool argument visible to the model.
@Component
public class HrTools {

    private static final int SEARCH_K = 5;
    private final RetrieverService retrieverService;

    public HrTools(RetrieverService retrieverService) {
        this.retrieverService = retrieverService;
    }

    // Returns the tool definitions array for the Anthropic Messages API request body.
    public List<Map<String, Object>> toolDefinitions() {
        Map<String, Object> searchTool = Map.of(
            "name", "searchHrDocuments",
            "description",
                "Search indexed HR documents for the authenticated user's office. " +
                "Returns the most relevant excerpts with source metadata. " +
                "The office is bound server-side and is NOT a parameter — you cannot change it. " +
                "IMPORTANT: Use concise keyword-focused queries, NOT full question sentences. " +
                "Extract the core topic from the user's question. " +
                "Examples: prefer 'RAS internal system' over 'Šta znate o RAS-u', " +
                "'godišnji odmor dani' over 'Koliko dana godišnjeg odmora imam', " +
                "'sick leave bolovanje' over 'How do I apply for sick leave'. " +
                "If you suspect content may exist in multiple languages, call this tool " +
                "a second time with an alternative keyword formulation.",
            "input_schema", Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of(
                        "type", "string",
                        "description", "Short keyword query (3-6 words) in EN, SR, or SQ — NOT a full question sentence."
                    )
                ),
                "required", List.of("query")
            )
        );

        Map<String, Object> metaTool = Map.of(
            "name", "getDocumentMetadata",
            "description",
                "Get the last-modified date and source URL for a specific HR document by name. " +
                "Only returns metadata for documents in the authenticated user's office.",
            "input_schema", Map.of(
                "type", "object",
                "properties", Map.of(
                    "sourceName", Map.of(
                        "type", "string",
                        "description", "The document file name as returned by searchHrDocuments."
                    )
                ),
                "required", List.of("sourceName")
            )
        );

        return List.of(searchTool, metaTool);
    }

    // Execute a tool call from the model. Citations list is populated as a side effect.
    public String execute(String toolName, Map<String, Object> input, String office,
                          List<Citation> citations) {
        return switch (toolName) {
            case "searchHrDocuments" -> {
                String query = Objects.toString(input.get("query"), "");
                yield searchHrDocuments(query, office, citations);
            }
            case "getDocumentMetadata" -> {
                String sourceName = Objects.toString(input.get("sourceName"), "");
                yield getDocumentMetadata(sourceName, office);
            }
            default -> "Unknown tool: " + toolName;
        };
    }

    private String searchHrDocuments(String query, String office, List<Citation> citations) {
        List<RetrievedChunk> chunks = retrieverService.retrieve(query, office, SEARCH_K);
        if (chunks.isEmpty()) {
            return "No relevant documents found in the " + office + " office HR documents.";
        }
        StringBuilder sb = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            String pageLabel = chunk.page() != null ? "Page/Slide " + chunk.page() : "N/A";
            sb.append("[").append(chunk.sourceName())
              .append(" | ").append(chunk.lastModified())
              .append(" | ").append(pageLabel).append("]\n")
              .append(chunk.content()).append("\n\n");

            // Populate citations (de-duplicate by sourceName + page)
            boolean exists = citations.stream().anyMatch(c ->
                Objects.equals(c.sourceName(), chunk.sourceName()) &&
                Objects.equals(c.page(), chunk.page()));
            if (!exists) {
                citations.add(new Citation(
                    chunk.sourceName(),
                    chunk.lastModified(),
                    chunk.sourceUrl(),
                    chunk.page()
                ));
            }
        }
        return sb.toString().trim();
    }

    private String getDocumentMetadata(String sourceName, String office) {
        List<RetrievedChunk> chunks = retrieverService.retrieve(sourceName, office, 3);
        return chunks.stream()
            .filter(c -> sourceName.equalsIgnoreCase(c.sourceName()))
            .findFirst()
            .map(c -> c.sourceName() + " | Last modified: " + c.lastModified() +
                      (c.sourceUrl() != null ? " | URL: " + c.sourceUrl() : ""))
            .orElse("Document not found in " + office + " office: " + sourceName);
    }
}
