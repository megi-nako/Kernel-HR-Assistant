# LLM Utilization — Kernel HR Assistant

This document precisely describes every LLM interaction in the system: which models are called, with what parameters, what design choices were made and why, and how the system is engineered to maximize grounding, minimize hallucination, and work correctly across three languages.

---

## 1. Models Used

| Role | Model | Provider | Access Method |
|------|-------|----------|---------------|
| Reasoning & generation | `claude-opus-4-8` | Anthropic | Direct HTTP (`java.net.http.HttpClient`) |
| Embeddings (primary) | `voyage-3` | Voyage AI | Direct HTTP REST (`/v1/embeddings`) |
| Embeddings (fallback) | `paraphrase-multilingual-MiniLM-L12-v2` | HuggingFace via DJL | Local inference, no API key |

---

## 2. Claude Opus 4.8 — Reasoning Agent

### 2.1 Why Claude Opus 4.8

- **Tool use reliability**: Claude Opus 4.8 has the highest tool-call accuracy among available models. For a RAG agent where every response *must* call `searchHrDocuments` before generating text, a model that occasionally decides to respond without searching is a correctness bug, not a quality issue.
- **Multilingual instruction following**: The system prompt instructs Claude to reply in a specific language detected at runtime (en/sr/sq). Claude Opus consistently respects this constraint even when the retrieved document excerpts are in a different language than the reply.
- **Direct HTTP, no SDK**: The team chose `java.net.http.HttpClient` over the Anthropic Java SDK to eliminate SDK version-pinning on corporate Maven mirrors. The raw API surface is stable and fully documented.

### 2.2 API Call Structure

Every chat query makes **one or more calls** to the Anthropic Messages API at `POST https://api.anthropic.com/v1/messages`. The request body is:

```json
{
  "model": "claude-opus-4-8",
  "max_tokens": 4096,
  "system": "<system prompt — see §2.3>",
  "messages": [
    {"role": "user",      "content": "...history turn 1..."},
    {"role": "assistant", "content": "...history turn 1 response..."},
    {"role": "user",      "content": "<current question>"}
  ],
  "tools": [<searchHrDocuments>, <getDocumentMetadata>],
  "tool_choice": {"type": "any"}   // first iteration only
}
```

### 2.3 System Prompt

The system prompt is constructed at runtime, injecting `{office}` and `{language}` from the authenticated session and the detected query language:

```
You are the Kernel HR Assistant for the {office} office.

Reply ONLY in {language}.

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
  "I could not find an answer to your question in the {office} office HR documents."
REFUSE non-HR questions with:
  "I can only help with HR questions for the {office} office, based on our HR documents."
NEVER mention or speculate about the other office's documents or policies.
```

**Design decisions in this prompt:**

| Constraint | Reason |
|------------|--------|
| `Reply ONLY in {language}` | Language is auto-detected from the user's question; Claude must mirror it |
| `ALWAYS call searchHrDocuments first` | Without this, Claude sometimes answers from training data instead of retrieval |
| `extract core TOPIC ... concise keywords` | Full-sentence queries ("Šta znate o RAS-u?") hurt cosine similarity; short keyword queries ("RAS sistem") find the right chunks |
| `try a second search with different or English keywords` | Multilingual embedding scores can vary by phrasing; a second attempt catches misses |
| `NEVER use your own prior knowledge` | HR documents may differ from Claude's training data (e.g., company-specific leave policies) |
| `Cite every claim` | Grounds the answer; citations appear in the frontend |
| `NEVER mention the other office` | Enforces logical isolation beyond the SQL-level physical isolation |

### 2.4 Forced Tool Use (First Iteration)

On the **first API call** of every query, `tool_choice` is set to `{"type": "any"}`:

```java
if (forceToolUse) {
    body.put("tool_choice", Map.of("type", "any"));
}
```

This is invoked with `buildRequestBody(systemPrompt, messages, iteration == 0)`.

**Why this matters:** Without `tool_choice`, Claude occasionally decides a query is "trivial" or "non-informational" and returns a text response without calling `searchHrDocuments` first. This produces hallucinated or unsupported answers. `{"type": "any"}` *requires* Claude to invoke at least one tool before generating any text, guaranteeing that every answer is grounded in a retrieval result.

`tool_choice` is **not** set on iterations > 0, allowing Claude to chain multiple tool calls naturally (e.g., `searchHrDocuments` followed by `getDocumentMetadata`) and eventually produce a final text response.

### 2.5 Tool-Use Loop

```
iteration 0:
  request + tool_choice={type:"any"}
  → Claude MUST call searchHrDocuments
  → tool result appended to messages

iteration 1 (if needed):
  request (no tool_choice)
  → Claude may call more tools OR produce end_turn
  → if tool_use: append to messages, continue loop
  → if end_turn: extract text, break

MAX_TOOL_ITERATIONS = 5   // safety ceiling; typical queries finish in 2 iterations
```

A typical successful query uses **exactly 2 iterations**: one `tool_use` (searchHrDocuments) and one `end_turn` (grounded text). Complex queries (e.g., involving multiple documents or needing metadata) use 3–4 iterations.

### 2.6 Multi-Turn Conversation

History is passed as alternating `user`/`assistant` messages prepended to the current question. History is **capped at 20 entries (10 turns)** to stay within context limits:

```java
List<Map<String, String>> capped = history.size() > 20
    ? history.subList(history.size() - 20, history.size())
    : history;
```

History is provided by the frontend as `[{role, content}]` pairs. The backend does **not** validate history content — it trusts the client to send only prior assistant/user turns, not system messages or tool results.

### 2.7 Token Budget

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `max_tokens` | 4096 | Enough for a detailed HR policy explanation with citations |
| Tool result size | ~5 × 800 chars ≈ 4,000 chars | `SEARCH_K=5` chunks, each ~800 chars; fits within context window |
| History cap | 20 messages | 10 turns × ~200 tokens avg = 2,000 tokens; leaves room for retrieval context |

---

## 3. Tool Definitions

The tool schemas are passed verbatim to the Anthropic Messages API in the `tools` array.

### 3.1 `searchHrDocuments`

```json
{
  "name": "searchHrDocuments",
  "description": "Search indexed HR documents for the authenticated user's office. Returns the most relevant excerpts with source metadata. The office is bound server-side and is NOT a parameter — you cannot change it. IMPORTANT: Use concise keyword-focused queries, NOT full question sentences. Extract the core topic from the user's question. Examples: prefer 'RAS internal system' over 'Šta znate o RAS-u', 'godišnji odmor dani' over 'Koliko dana godišnjeg odmora imam', 'sick leave bolovanje' over 'How do I apply for sick leave'. If you suspect content may exist in multiple languages, call this tool a second time with an alternative keyword formulation.",
  "input_schema": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "Short keyword query (3-6 words) in EN, SR, or SQ — NOT a full question sentence."
      }
    },
    "required": ["query"]
  }
}
```

**Tool implementation (server-side):**
1. `RetrieverService.retrieve(query, office, k=5)` — embeds query with Voyage-3, searches PostgreSQL with mandatory `WHERE office = ?`
2. Formats result: `[sourceName | lastModified | Page N]\ncontent\n\n`
3. Populates `citations[]` list (deduplicated by sourceName + page)
4. Returns formatted string → appended to messages as `tool_result`

**Why keywords not sentences:** Voyage-3 uses a shared embedding space for all languages. When the query is a full natural-language question ("Šta znate o RAS-u?"), the embedding encodes the *speech act* (asking for knowledge) as well as the *topic* (RAS). The cosine similarity between this vector and a document chunk containing "Šta je RAS? RAS je Interni administrativni sistem..." is lower than the similarity between "RAS sistem" and that same chunk. Keyword queries concentrate the vector on the topic, improving retrieval precision across languages.

### 3.2 `getDocumentMetadata`

```json
{
  "name": "getDocumentMetadata",
  "description": "Get the last-modified date and source URL for a specific HR document by name. Only returns metadata for documents in the authenticated user's office.",
  "input_schema": {
    "type": "object",
    "properties": {
      "sourceName": {
        "type": "string",
        "description": "The document file name as returned by searchHrDocuments."
      }
    },
    "required": ["sourceName"]
  }
}
```

**Tool implementation:** Searches by `sourceName` as a query (semantic match), then filters for exact `sourceName` equality. Returns `sourceName | Last modified: ... | URL: ...` as a string.

---

## 4. Voyage AI — Multilingual Embeddings

### 4.1 Why Voyage-3

- **1024-dimensional** shared multilingual embedding space covering 100+ languages
- Consistently top-ranked for multilingual retrieval on MIRACL and BEIR benchmarks
- Anthropic recommends Voyage AI as their preferred embedding partner
- Single model handles EN, SR (Serbian), and SQ (Albanian) without language-specific variants
- `voyage-3` outperforms OpenAI `text-embedding-3-large` on cross-lingual retrieval tasks

### 4.2 Embedding Call Pattern

```java
POST https://api.voyageai.com/v1/embeddings
Authorization: Bearer {VOYAGE_API_KEY}
Content-Type: application/json

{
  "model": "voyage-3",
  "input": ["chunk text 1", "chunk text 2", ...]
}

Response:
{
  "data": [
    {"embedding": [0.023, -0.014, ...], "index": 0},
    ...
  ]
}
```

**Same model for documents and queries.** The system does not use `input_type` ("query" vs "document") differentiation. Both stored embeddings and query embeddings use the default symmetric mode. This ensures the cosine distance between a query vector and a stored chunk vector is meaningful and consistent.

### 4.3 Rate Limit Management

Voyage-3 free tier: **3 RPM** (requests per minute).

The client implements:
- **Batch size: 8 texts per request** — keeps token counts well under the 10K TPM limit
- **21-second inter-batch delay** — ensures ≤ 2.8 RPM during bulk ingestion
- **Exponential backoff on 429**: initial 22s, doubles each retry, max 120s, up to 6 retries

For query-time embedding (ScopeGuard + searchHrDocuments): each query costs **2 Voyage API calls** (one for ScopeGuard pre-check, one for the actual search). With 3 RPM, burst capacity is 1 concurrent query. This is acceptable for a demo deployment; a production deployment would use paid Voyage tier or a caching layer.

### 4.4 Embedding Fallback (DJL)

If `VOYAGE_API_KEY` is absent or `EMBEDDING_PROVIDER=djl` is set, the system falls back to:

```
DJL (Deep Java Library) + HuggingFace Hub
Model: sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
Dimensions: 384
Languages: 50+ (includes SR and SQ)
```

The DJL client implements the same `EmbeddingClient` interface. Documents indexed with DJL vectors and queried with DJL vectors work correctly — the only degradation is retrieval quality (384-dim vs 1024-dim) and the loss of the shared EN/SR/SQ embedding space quality.

Documents indexed with Voyage embeddings **cannot be queried** with DJL embeddings — the vector spaces are incompatible. A re-index is required when switching providers.

---

## 5. Language Detection

Language detection determines the `{language}` variable in the system prompt, which controls the language of Claude's response.

### 5.1 Detection Chain

```
LanguageDetector.detect(question)
  │
  ├── text.length < 10 → "en" (too short to detect)
  │
  ├── Tika OptimaizeLangDetector.detect(text)
  │     If result is non-null AND isReasonablyCertain() AND language ∈ {en, sr, sq}
  │     → return detected language
  │
  └── Keyword fallback (when Tika is uncertain or unavailable):
        Split text on whitespace/punctuation
        Count hits in SQ_WORDS (Albanian: "dhe", "një", "për", ...)
        Count hits in SR_WORDS (Serbian: "šta", "kako", "znate", "zaposleni", ...)
        sqCount > srCount → "sq"
        srCount > sqCount → "sr"
        (tie or both zero) → "en"
```

### 5.2 Serbian Keyword List

The keyword list covers both common function words and HR/domain-specific terms found in Serbia office documents:

```java
Set.of(
    // Function words
    "kako", "što", "šta", "koji", "koja", "koje", "kada", "gde", "gdje", "zašto", "koliko",
    // Common verbs
    "znate", "znam", "znači", "možete", "možemo", "može", "moram", "moramo",
    "ima", "imam", "imamo", "treba", "trebam",
    // HR domain terms
    "zaposleni", "zaposlenog", "radnik", "radnika", "kompanija", "pravilnik",
    "godišnji", "odmor", "odmora", "bolovanje", "zahtev", "ugovor", "ugovora",
    // Insurance terms (Delta Generali policy docs)
    "limita", "učešća", "isključenje", "naknada", "naknade", "osiguranje", "osiguranja"
)
```

**Design note:** Language detection affects only the *reply language*. It has zero effect on the office assignment, the vector search, or which documents are retrieved. A Serbian-language query from an Albania office user will still search only Albania office documents and reply in Serbian if detected.

---

## 6. ScopeGuard — Pre-LLM Semantic Gate

### 6.1 Purpose

The ScopeGuard prevents completely off-topic queries (weather, general coding help, math homework) from consuming an Anthropic API call. It performs a fast pre-flight vector search to check semantic relevance before invoking the Claude agent.

### 6.2 Logic

```java
int docCount = vectorStore.countByOffice(office);
if (docCount == 0) {
    // Bypass in dev mode when no documents are indexed yet
    return Optional.empty();
}

List<RetrievedChunk> results = retrieverService.retrieve(query, office, 1);
double bestScore = results.isEmpty() ? 0.0 : results.get(0).score();
log.debug("ScopeGuard check office={} threshold={} bestScore={}", office, threshold, bestScore);

if (bestScore < threshold) {
    return Optional.of("I can only help with HR questions for the " + office + " office...");
}
return Optional.empty();  // proceed to agent
```

### 6.3 Threshold Calibration

The threshold is `0.10` (configurable via `SCOPE_THRESHOLD` env var).

**Why 0.10 and not higher:**

Voyage-3 cross-lingual cosine similarity scores for legitimate HR queries against the Serbia document corpus (Serbian-language PDFs) when queried in English:
- "what is RAS?" against Serbian RAS explanation chunk: ~0.28–0.35
- "vacation days" against Serbian "odmor" chunk: ~0.25–0.32

These scores are lower than same-language queries because:
1. The embedding space must bridge two languages
2. RAS is a company-specific acronym with no pre-training signal

A threshold of 0.35 (original) falsely refused these legitimate queries. A threshold of 0.10 passes all HR-related queries while still blocking true non-sequiturs (e.g., "what is 2+2" or "write me a poem") which score below 0.08 against the HR corpus.

Claude's system prompt provides a second line of defense: even if a borderline query passes the 0.10 gate, Claude will check the retrieved chunks and respond "I could not find an answer" if no relevant content is found.

---

## 7. Grounding Protocol Summary

The system enforces grounding through five coordinated mechanisms:

| # | Mechanism | Where | Enforces |
|---|-----------|-------|---------|
| 1 | ScopeGuard cosine gate | Pre-LLM | Query must semantically relate to HR documents |
| 2 | `tool_choice: {type:"any"}` on first turn | LLM API | Claude MUST retrieve before generating |
| 3 | System prompt: `ALWAYS call searchHrDocuments first` | System prompt | Claude instruction to search before responding |
| 4 | System prompt: `NEVER use your own prior knowledge` | System prompt | Claude must cite retrieved chunks |
| 5 | Tool result as sole context | Message history | Claude only has access to retrieved excerpts, not the full document corpus |

No single mechanism alone is sufficient. The combination makes it structurally difficult for the system to generate an answer that isn't backed by a retrieved document chunk.

---

## 8. Observability

### Audit Log

Every query is appended to `data/audit.log` synchronously:

```
2026-06-18T15:43:57Z | Marko.Nikolic@eng.it | serbia | answered | Q: what is RAS? | A: RAS je Interni...
2026-06-18T15:44:12Z | Agathi.Koci@eng.al   | albania | refused  | Q: what's the weather? | A: I can only help...
```

Fields: `timestamp | upn | office | answered|refused | Q: <question[:200]> | A: <response[:300]>`

### Debug Logging

The following DEBUG-level logs are available when `com.kernel.hr: DEBUG` is set:

```
ScopeGuard check office=serbia threshold=0.10 bestScore=0.31 query='what is RAS?'
Tool 'searchHrDocuments' result length: 3841
HrAgentService: iteration 0, stop_reason=tool_use
HrAgentService: iteration 1, stop_reason=end_turn
```

---

## 9. Cost and Performance Profile

| Operation | API Calls | Latency (est.) |
|-----------|-----------|----------------|
| ScopeGuard (refused) | 1 Voyage | ~300ms |
| ScopeGuard (passed) + 1 search | 2 Voyage + 1 Claude (2 iterations) | ~3–6s |
| Complex query (2 searches + metadata) | 3 Voyage + 1 Claude (4 iterations) | ~8–12s |
| Document ingestion (Serbia ~24 docs, ~660 chunks) | ~83 Voyage calls (batched, rate-limited) | ~30 min on free tier |

**Production path:** Use paid Voyage tier (no RPM limit, millisecond latency). Cache query embeddings for repeated questions. Use Claude Haiku for simple yes/no HR lookups and reserve Opus for synthesis tasks.
