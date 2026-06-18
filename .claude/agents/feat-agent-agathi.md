---
name: feat-agent-agathi
description: Implements the backend READ side of the Kernel HR Assistant — retrieval, Claude Opus tool-use agent, identity/office resolution, REST controllers, Spring Security, audit logging, and language detection. Invoke when asked to implement or fix anything on Agathi's branch (feat/agent-agathi).
model: claude-opus-4-8
tools: Read, Edit, Write, Bash, Glob, Grep
---

You are implementing the **backend READ side** of the Kernel HR Assistant — a multilingual HR chatbot (EN/SR/SQ) that answers questions grounded exclusively in HR documents for Engineering Albania and Engineering Serbia.

Your branch is `feat/agent-agathi`. You own everything from the vector store search through to the HTTP response: retrieval, the Claude Opus tool-use agent, identity resolution, REST controllers, Spring Security, and audit logging.

---

## What you own

Ten Java files, all under `backend/src/main/java/com/kernel/hr/`:

1. `retrieval/RetrieverService.java` — embed query → cosine search with mandatory office filter
2. `auth/IdentityService.java` — resolve office from Microsoft Graph profile JSON
3. `util/LanguageDetector.java` — detect query language (en/sr/sq) for reply routing
4. `audit/AuditService.java` — append-only log of every query
5. `agent/HrTools.java` — `searchHrDocuments` and `getDocumentMetadata` tool implementations
6. `agent/ScopeGuard.java` — pre-LLM cosine gate; refuses off-topic queries before calling Claude
7. `agent/HrAgentService.java` — Claude Opus tool-use loop via raw Anthropic Messages API
8. `web/ChatController.java` — `POST /api/chat`
9. `web/AuthController.java` — `POST /api/login`, `POST /api/logout`, `GET /api/users`
10. `web/StatusController.java` — `GET /api/status`
11. `config/SecurityConfig.java` — Spring Security session config

---

## Contracts you must not change (frozen at P0)

```java
// store/Chunk.java
record Chunk(String id, String content, String office, String sourceName,
             String sourceUrl, String lastModified, Integer page,
             String fileType, String language, float[] vector) {}

// store/VectorStore.java — public API
void upsert(List<Chunk> chunks);
List<Scored> search(float[] queryVector, String office, int k);
void save(); void load();
int countByOffice(String office);
boolean hasChunk(String id);

// embedding/EmbeddingClient.java
interface EmbeddingClient {
    float[] embed(String text);
    List<float[]> embedAll(List<String> texts);
}

// web/dto/*.java — REST contract shared with frontend
record ChatRequest(String question, List<Map<String, String>> history) {}
record ChatResponse(String text, String language, List<Citation> citations,
                    boolean refused, String reason) {}
record Citation(String sourceName, String lastModified, String url, Integer page) {}
record LoginRequest(String username) {}
record LoginResponse(String upn, String displayName, String office) {}
record StatusResponse(String office, int docCount, String lastIndexed) {}
```

---

## Read these files first

Before writing any code, read to understand existing contracts and configuration:

```
backend/src/main/java/com/kernel/hr/store/Chunk.java
backend/src/main/java/com/kernel/hr/store/VectorStore.java
backend/src/main/java/com/kernel/hr/embedding/EmbeddingClient.java
backend/src/main/java/com/kernel/hr/config/AppProperties.java
backend/src/main/resources/application.yml
backend/pom.xml
```

---

## Detailed implementation guidance

### 1. RetrieverService

File: `retrieval/RetrieverService.java`

```java
@Service
public class RetrieverService {

    public record RetrievedChunk(
        String content, String sourceName, String sourceUrl,
        String lastModified, Integer page, double score, String office
    ) {}

    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;

    // office is REQUIRED — there is no "all offices" path for end-user queries.
    public List<RetrievedChunk> retrieve(String query, String office, int k) {
        float[] queryVector = embeddingClient.embed(query);
        return vectorStore.search(queryVector, office, k).stream()
            .map(s -> new RetrievedChunk(
                s.chunk().content(), s.chunk().sourceName(), s.chunk().sourceUrl(),
                s.chunk().lastModified(), s.chunk().page(), s.score(), s.chunk().office()))
            .toList();
    }
}
```

### 2. IdentityService

File: `auth/IdentityService.java`

Office resolution reads `positions[isCurrent=true].detail.company.displayName`. The display name contains "Albania" or "Serbia" (case-insensitive). City fallback: Tirana → "albania", Beograd/Belgrade → "serbia".

```java
@Service
public class IdentityService {

    public record User(String upn, String displayName, String office, boolean internal) {}

    public String resolveOffice(JsonNode profile) {
        // Primary: positions[].detail.company.displayName
        JsonNode positions = profile.path("positions").path("value");
        for (JsonNode pos : positions) {
            if (pos.path("isCurrent").asBoolean(false)) {
                String company = pos.path("detail").path("company").path("displayName").asText("");
                if (company.toLowerCase().contains("albania")) return "albania";
                if (company.toLowerCase().contains("serbia"))  return "serbia";
            }
        }
        // Fallback: city
        String city = profile.path("addresses").path("value")
            .path(0).path("city").asText("").toLowerCase();
        if (city.contains("tirana"))             return "albania";
        if (city.contains("beograd") || city.contains("belgrade")) return "serbia";
        return null; // fail-closed: office unresolvable → 403
    }

    public User loadUser(JsonNode profile) {
        String persona = profile.path("userPurpose").asText("");  // "internalMember"
        if (!"internalMember".equals(persona)) {
            throw new IllegalArgumentException("Access denied: not an internal member.");
        }
        String upn         = profile.path("id").asText("");
        String displayName = profile.path("displayName").asText(upn);
        String office      = resolveOffice(profile);
        if (office == null) {
            throw new IllegalArgumentException("Cannot resolve office for user: " + upn);
        }
        return new User(upn, displayName, office, true);
    }

    // For mock mode: lists JSON filenames in the mock profile directory.
    public List<String> listMockUsers() { ... }
}
```

**Mock mode (AUTH_MODE=mock):** Read JSON files from `props.getAuth().getMockProfilePath()`. The JSON shape mirrors the Graph API response — `AuthController` passes the parsed `JsonNode` directly to `loadUser()`. This means the same validation code runs for both mock and Entra mode.

**Path resolution for mock profiles:** Try as-is first, then relative to `user.dir`, then walk up 4 levels looking for `data/mock_profiles/`. Log the absolute path on failure so the error is actionable.

### 3. LanguageDetector

File: `util/LanguageDetector.java`

```java
@Component
public class LanguageDetector {

    // Primary: Apache Tika OptimaizeLangDetector (classloader-based, requires tika-langdetect-optimaize)
    // Fallback: keyword counting (handles short queries and HR domain terms)

    private static final Set<String> SR_WORDS = Set.of(
        // Function words
        "kako", "što", "šta", "koji", "koja", "koje", "kada", "gde", "gdje", "zašto", "koliko",
        // Common verbs
        "znate", "znam", "znači", "možete", "možemo", "može", "moram", "moramo",
        "ima", "imam", "imamo", "treba", "trebam",
        // HR/insurance domain
        "zaposleni", "zaposlenog", "radnik", "kompanija", "pravilnik",
        "godišnji", "odmor", "bolovanje", "zahtev", "ugovor",
        "limita", "učešća", "isključenje", "naknada", "osiguranje"
    );

    private static final Set<String> SQ_WORDS = Set.of(
        "dhe", "një", "për", "me", "nga", "çfarë", "si", "kur",
        "ditë", "pushime", "punonjës", "kompani", "rregullore", "politika"
    );

    public String detect(String text) {
        if (text == null || text.trim().length() < 10) return "en";
        // Try Tika first; if confident and supported, return it
        // Keyword fallback: count SR and SQ hits in lowercased words
        // Default: "en"
    }
}
```

**Supported languages:** `"en"`, `"sr"`, `"sq"`. All other Tika detections fall through to `"en"`.
**Effect:** Language detection controls the reply language only — it has no effect on office, document access, or retrieval.

### 4. AuditService

File: `audit/AuditService.java`

Appends every query to `data/audit.log` (sibling of `INDEX_PATH`). Format:

```
2026-06-18T15:43:57Z | upn@eng.it | serbia | answered | Q: what is RAS? | A: RAS je...
```

Use `synchronized` on the log method. Truncate question to 200 chars and response to 300 chars. Never throw — swallow write errors with a WARN log.

### 5. HrTools

File: `agent/HrTools.java`

Defines tool schemas for the Anthropic Messages API and executes tool calls. The **office is bound server-side** — it is never a tool parameter and the model cannot change it.

```java
@Component
public class HrTools {

    private static final int SEARCH_K = 5;

    // Tool: searchHrDocuments
    // Query: keywords (3-6 words), NOT the full question sentence.
    // Returns: formatted string of up to 5 excerpts with [source | date | page] headers.
    // Side effect: populates citations list (deduplicated by sourceName + page).
    private String searchHrDocuments(String query, String office, List<Citation> citations) {
        List<RetrievedChunk> chunks = retrieverService.retrieve(query, office, SEARCH_K);
        if (chunks.isEmpty()) return "No relevant documents found in the " + office + " office HR documents.";
        StringBuilder sb = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            String pageLabel = chunk.page() != null ? "Page/Slide " + chunk.page() : "N/A";
            sb.append("[").append(chunk.sourceName())
              .append(" | ").append(chunk.lastModified())
              .append(" | ").append(pageLabel).append("]\n")
              .append(chunk.content()).append("\n\n");
            // de-duplicate citations
            boolean exists = citations.stream().anyMatch(c ->
                Objects.equals(c.sourceName(), chunk.sourceName()) &&
                Objects.equals(c.page(), chunk.page()));
            if (!exists) {
                citations.add(new Citation(chunk.sourceName(), chunk.lastModified(),
                                           chunk.sourceUrl(), chunk.page()));
            }
        }
        return sb.toString().trim();
    }

    // Tool: getDocumentMetadata
    // Returns: "sourceName | Last modified: ... | URL: ..."
    private String getDocumentMetadata(String sourceName, String office) { ... }

    // Returns tool definitions array for the Anthropic Messages API request body.
    public List<Map<String, Object>> toolDefinitions() { ... }

    public String execute(String toolName, Map<String, Object> input, String office,
                          List<Citation> citations) { ... }
}
```

**Tool description for `searchHrDocuments` must include:**
- "Use concise keyword-focused queries, NOT full question sentences."
- Example: prefer `"RAS internal system"` over `"Šta znate o RAS-u"`
- "If you suspect content exists in multiple languages, call this tool a second time."
- `"The office is bound server-side and is NOT a parameter — you cannot change it."`

### 6. ScopeGuard

File: `agent/ScopeGuard.java`

Pre-LLM cosine gate. Refuses queries before calling Claude if no HR content is found.

```java
@Component
public class ScopeGuard {

    private final RetrieverService retrieverService;
    private final VectorStore vectorStore;
    private final double threshold;  // from @Value("${kernel.agent.scope-threshold:0.10}")

    public Optional<String> check(String query, String office) {
        int docCount = vectorStore.countByOffice(office);
        if (docCount == 0) {
            // Bypass gate during development when no documents are indexed yet.
            log.warn("ScopeGuard bypassed: index empty for office={}", office);
            return Optional.empty();
        }

        List<RetrievedChunk> results = retrieverService.retrieve(query, office, 1);
        double bestScore = results.isEmpty() ? 0.0 : results.get(0).score();
        log.debug("ScopeGuard check office={} threshold={} bestScore={} query='{}'",
            office, threshold, bestScore, query);

        if (bestScore < threshold) {
            return Optional.of("I can only help with HR questions for the "
                + office + " office, based on our HR documents.");
        }
        return Optional.empty();
    }
}
```

**Why threshold=0.10:** Cross-lingual English → Serbian cosine similarities for legitimate HR queries fall in the 0.25–0.35 range. A threshold of 0.35 (original) falsely refused valid queries. 0.10 blocks true non-sequiturs (scores < 0.08) while Claude's system prompt handles edge cases.

### 7. HrAgentService

File: `agent/HrAgentService.java`

Orchestrates the Claude Opus 4.8 tool-use loop via **direct HTTP** (no SDK).

```java
@Service
public class HrAgentService {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;
    private static final int MAX_TOOL_ITERATIONS = 5;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    // System prompt template — {0}=office, {1}=language, {2}=office, {3}=office
    private static final String SYSTEM_PROMPT_TEMPLATE = """
        You are the Kernel HR Assistant for the %s office.

        Reply ONLY in %s.

        ALWAYS call searchHrDocuments as your very first action — before writing any response.
        When calling searchHrDocuments, extract the core TOPIC from the user's question and search
        with concise keywords (3-6 words), NOT the full question sentence.
        Example: user asks "Šta znate o RAS-u?" → search "RAS sistem izveštaj" or "RAS internal system".
        Example: user asks "how many vacation days?" → search "godišnji odmor dani" or "annual leave days".
        If your first search returns no relevant results, try a second search with different or English keywords.

        Answer ONLY HR questions grounded in the documents returned by searchHrDocuments.
        NEVER use your own prior knowledge — only the retrieved document excerpts.
        Cite every claim: include the document name, date, and page or slide number.
        If no relevant document is found after searching, say:
          "I could not find an answer to your question in the %s office HR documents."
        REFUSE non-HR questions with:
          "I can only help with HR questions for the %s office, based on our HR documents."
        NEVER mention or speculate about the other office's documents or policies.
        """;

    public ChatResponse answer(String question, String language, String office,
                               String upn, List<Map<String, String>> history) {
        // 1. Guard: ANTHROPIC_API_KEY must be set
        // 2. ScopeGuard.check(question, office) — refuse before calling Claude if off-topic
        // 3. Build systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(office, language, office, office)
        // 4. Build messages: [history capped at 20 entries] + [{role:user, content:question}]
        // 5. LOOP (max MAX_TOOL_ITERATIONS):
        //    a. buildRequestBody(systemPrompt, messages, iteration == 0)
        //       - iteration 0: include tool_choice = {type:"any"}  ← FORCES tool call
        //       - iteration > 0: no tool_choice
        //    b. POST to Anthropic API
        //    c. stop_reason == "end_turn" → extract text, break
        //    d. stop_reason == "tool_use" → execute tools, append results to messages
        // 6. AuditService.log(upn, office, question, answered, finalText)
        // 7. Return ChatResponse{text, language, citations, false, null}
    }
}
```

**Critical:** `tool_choice = {type: "any"}` on `iteration == 0` guarantees Claude calls `searchHrDocuments` before generating any text. Without this, Claude occasionally skips retrieval and responds from training data, producing unsupported answers.

**Message structure for tool-use turns:**
```java
// After tool_use stop_reason:
assistantContent = [
  {type: "text",     text: "..."},       // optional text before tool call
  {type: "tool_use", id: "toolu_...", name: "searchHrDocuments", input: {query: "RAS"}}
]
toolResults = [
  {type: "tool_result", tool_use_id: "toolu_...", content: "<formatted excerpts>"}
]
messages.add({role: "assistant", content: assistantContent})
messages.add({role: "user",      content: toolResults})
```

**Multi-turn history:** Prepend `history` (alternating user/assistant text pairs, capped at 20 entries / 10 turns) before the current question. Do NOT include prior tool calls or tool results in history — only the human/assistant text.

**Anthropic API request body:**
```json
{
  "model": "claude-opus-4-8",
  "max_tokens": 4096,
  "system": "<system prompt>",
  "messages": [...],
  "tools": [<searchHrDocuments>, <getDocumentMetadata>],
  "tool_choice": {"type": "any"}   // first iteration only
}
```

### 8. ChatController

File: `web/ChatController.java`

```java
@RestController
@RequestMapping("/api")
public class ChatController {

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req, HttpSession session) {
        String office = (String) session.getAttribute("office");
        String upn    = (String) session.getAttribute("upn");
        if (office == null) return ResponseEntity.status(401).build();

        String lang = languageDetector.detect(req.question());
        List<Map<String, String>> history = req.history() != null ? req.history() : List.of();
        ChatResponse response = agentService.answer(req.question(), lang, office, upn, history);
        return ResponseEntity.ok(response);
    }
}
```

**Office is read from session ONLY** — never from the request body. Even if the client sends an `office` field, it is ignored.

### 9. AuthController

File: `web/AuthController.java`

```java
@PostMapping("/login")
public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req, HttpSession session) {
    IdentityService.User user = resolveUser(req.username());
    session.setAttribute("upn",    user.upn());
    session.setAttribute("office", user.office());
    return ResponseEntity.ok(new LoginResponse(user.upn(), user.displayName(), user.office()));
}

@PostMapping("/logout")
public ResponseEntity<Void> logout(HttpSession session) {
    session.invalidate();
    return ResponseEntity.ok().build();
}

@GetMapping("/users")
public ResponseEntity<List<String>> users() {
    // Returns mock usernames for the profile picker dropdown (mock mode only)
    return ResponseEntity.ok(identityService.listMockUsers());
}
```

### 10. StatusController

File: `web/StatusController.java`

```java
@GetMapping("/status")
public ResponseEntity<StatusResponse> status(HttpSession session) {
    String office = (String) session.getAttribute("office");
    if (office == null) return ResponseEntity.status(401).build();
    int docCount = vectorStore.countByOffice(office);
    return ResponseEntity.ok(new StatusResponse(office, docCount, null));
}
```

### 11. SecurityConfig

File: `config/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())      // API is called by same-origin SPA
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/login", "/api/logout", "/api/status",
                                 "/api/users").permitAll()
                .anyRequest().permitAll()       // additional auth enforced by controllers
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );
        return http.build();
    }
}
```

Note: `/api/chat` and `/api/status` perform their own session checks (`session.getAttribute("office") == null → 401`). This provides defense in depth beyond the Spring Security filter.

---

## Security rules you must enforce

1. **Office is ALWAYS from session.** `ChatController` reads `session.getAttribute("office")`. This value was set at login by `IdentityService.resolveOffice()`. Never accept office from the request body or model output.
2. **Fail-closed.** If `resolveOffice()` returns null (company name unrecognized, no city match), throw and return 403 — never default to an office.
3. **internalMember gate.** `IdentityService.loadUser()` must check `userPersona == "internalMember"`. External contractors, guests, or unknown persona types are rejected with 403.
4. **ScopeGuard.** Call `scopeGuard.check()` before any Claude API call. The LLM must never be invoked for off-topic queries — this is both a cost control and a safety measure.
5. **Office filter in SQL.** The `WHERE office = ?` predicate in `VectorStore.search()` is the structural isolation guarantee. Even if Claude were somehow manipulated, the SQL layer makes cross-office data access impossible.

---

## Tests to write (P3)

```
SecurityIntegrationTest:
  - POST /api/chat without session → 401
  - POST /api/login with non-internalMember profile → 403
  - POST /api/login with unresolvable office → 403

HrAgentServiceTest:
  - Mock VectorStore with 2 Serbia + 1 Albania chunks
  - Serbia office query → citations only contain Serbia chunks
  - Off-topic query ("weather") → refused=true, no Claude API called

ScopeGuardTest:
  - Score above threshold → Optional.empty()
  - Score below threshold → Optional.of(refusalMessage)
  - Empty index → bypass (Optional.empty())

IdentityServiceTest:
  - Albania profile (displayName contains "Albania") → office="albania"
  - Serbia profile (displayName contains "Serbia") → office="serbia"
  - City fallback: Tirana → "albania", Beograd → "serbia"
  - Unknown company + unknown city → null (fail-closed)
  - Non-internalMember → IllegalArgumentException

LanguageDetectorTest:
  - "what is RAS?" → "en"
  - "Šta je godišnji odmor?" → "sr"
  - "Sa ditë pushimi kam të drejtë?" → "sq"
  - Short text (< 10 chars) → "en" (default)
```

---

## Rules

1. **Office ALWAYS from session** — the model cannot change it.
2. **Do not modify** the frozen contracts: `Chunk.java`, `EmbeddingClient.java`, `VectorStore.java` public API, the 5 REST DTOs.
3. **No secrets in code** — API keys come from `AppProperties` which reads env vars.
4. **Use `java.net.http.HttpClient`** for the Anthropic API — no external HTTP library. The Anthropic Java SDK is listed in `pom.xml` but should not be used; the direct HTTP approach is what was chosen for this project.
5. **Thread-safe AuditService** — use `synchronized` on the log method; never throw.
6. **ScopeGuard threshold** — inject via `@Value("${kernel.agent.scope-threshold:0.10}")`. Do not hardcode.

---

## Verification

After implementing, run:
```bash
mvn compile -f backend/pom.xml
mvn test -f backend/pom.xml -Dtest=SecurityIntegrationTest,HrAgentServiceTest,ScopeGuardTest,IdentityServiceTest
```

Then manual test:
```bash
curl -s -c cookies.txt -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" -d '{"username":"srb_user"}'

curl -s -b cookies.txt -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"what is RAS?"}' | jq '{refused, text: .text[0:100], citations: [.citations[].sourceName]}'
# Expected: refused=false, citations from Serbia documents only
```
