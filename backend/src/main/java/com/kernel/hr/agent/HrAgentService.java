package com.kernel.hr.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kernel.hr.audit.AuditService;
import com.kernel.hr.config.AppProperties;
import com.kernel.hr.web.dto.ChatResponse;
import com.kernel.hr.web.dto.Citation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Single Claude agent with tool use — calls Anthropic Messages API directly via HttpClient.
// No SDK needed: we handle the JSON protocol with Jackson (already on classpath).
@Service
public class HrAgentService {

    private static final Logger log = LoggerFactory.getLogger(HrAgentService.class);

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;
    private static final int MAX_TOOL_ITERATIONS = 5;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
        You are the Kernel HR Assistant for the %s office.

        Reply ONLY in %s.
        Answer ONLY HR questions grounded in the documents retrieved by the searchHrDocuments tool.
        NEVER use your own prior knowledge — only retrieved document excerpts.
        Cite every claim: include the document name, date, and page or slide number.
        If no relevant document is found, say: "I could not find an answer to your question in the %s office HR documents."
        REFUSE non-HR questions with: "I can only help with HR questions for the %s office, based on our HR documents."
        NEVER mention or speculate about the other office's documents or policies.
        """;

    private final AppProperties props;
    private final HrTools hrTools;
    private final ScopeGuard scopeGuard;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HrAgentService(AppProperties props,
                          HrTools hrTools,
                          ScopeGuard scopeGuard,
                          AuditService auditService,
                          ObjectMapper objectMapper) {
        this.props = props;
        this.hrTools = hrTools;
        this.scopeGuard = scopeGuard;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public ChatResponse answer(String question, String language, String office) {
        return answer(question, language, office, "anonymous");
    }

    public ChatResponse answer(String question, String language, String office, String upn) {
        // Guard: API key must be set
        String apiKey = props.getAnthropic().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ANTHROPIC_API_KEY is not set — returning config message.");
            String msg = "[CONFIG] ANTHROPIC_API_KEY is not set. Add it to your environment and restart.";
            auditService.log(upn, office, question, false, msg);
            return new ChatResponse(msg, language, List.of(), false, null);
        }

        // Grounding gate (#2)
        Optional<String> refusal = scopeGuard.check(question, office);
        if (refusal.isPresent()) {
            auditService.log(upn, office, question, false, refusal.get());
            return new ChatResponse("", language, List.of(), true, refusal.get());
        }

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(office, language, office, office);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", question));

        List<Citation> citations = new ArrayList<>();
        String finalText = "";
        boolean answered = false;

        try {
            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                Map<String, Object> requestBody = buildRequestBody(systemPrompt, messages);
                String responseJson = callApi(apiKey, requestBody);
                JsonNode response = objectMapper.readTree(responseJson);

                String stopReason = response.path("stop_reason").asText("end_turn");
                JsonNode content = response.path("content");

                if ("end_turn".equals(stopReason)) {
                    // Extract the final text answer
                    for (JsonNode block : content) {
                        if ("text".equals(block.path("type").asText())) {
                            finalText = block.path("text").asText();
                        }
                    }
                    answered = true;
                    break;
                }

                if ("tool_use".equals(stopReason)) {
                    // Add assistant message with all content blocks
                    List<Map<String, Object>> assistantContent = new ArrayList<>();
                    List<Map<String, Object>> toolResults = new ArrayList<>();

                    for (JsonNode block : content) {
                        String type = block.path("type").asText();
                        if ("text".equals(type)) {
                            assistantContent.add(Map.of(
                                "type", "text",
                                "text", block.path("text").asText()
                            ));
                        } else if ("tool_use".equals(type)) {
                            String toolId = block.path("id").asText();
                            String toolName = block.path("name").asText();
                            @SuppressWarnings("unchecked")
                            Map<String, Object> toolInput = objectMapper.convertValue(
                                block.path("input"), Map.class);

                            assistantContent.add(Map.of(
                                "type", "tool_use",
                                "id", toolId,
                                "name", toolName,
                                "input", toolInput
                            ));

                            // Execute the tool (office bound server-side)
                            String toolResult = hrTools.execute(toolName, toolInput, office, citations);
                            log.debug("Tool '{}' result length: {}", toolName, toolResult.length());

                            toolResults.add(Map.of(
                                "type", "tool_result",
                                "tool_use_id", toolId,
                                "content", toolResult
                            ));
                        }
                    }

                    messages.add(Map.of("role", "assistant", "content", assistantContent));
                    messages.add(Map.of("role", "user", "content", toolResults));
                }
            }

            if (!answered) {
                log.warn("Tool-use loop exhausted {} iterations for office={}", MAX_TOOL_ITERATIONS, office);
                finalText = "I was unable to complete processing your request. Please try again.";
            }

        } catch (Exception e) {
            log.error("HrAgentService error for office={}: {}", office, e.getMessage(), e);
            String errMsg = "An error occurred while processing your request. Please try again.";
            auditService.log(upn, office, question, false, errMsg);
            return new ChatResponse(errMsg, language, List.of(), false, null);
        }

        auditService.log(upn, office, question, answered, finalText);
        return new ChatResponse(finalText, language, citations, false, null);
    }

    private Map<String, Object> buildRequestBody(String systemPrompt,
                                                  List<Map<String, Object>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getAnthropic().getModel());
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", systemPrompt);
        body.put("messages", messages);
        body.put("tools", hrTools.toolDefinitions());
        return body;
    }

    private String callApi(String apiKey, Map<String, Object> requestBody) throws Exception {
        String json = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ANTHROPIC_API_URL))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Anthropic API error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Anthropic API returned HTTP " + response.statusCode()
                + ": " + response.body());
        }
        return response.body();
    }
}
