# Kernel HR Assistant — Agent Specification

---

## 1. Problem Statement

Engineering Albania (Tirana) and Engineering Serbia (Belgrade) employees need instant, accurate answers to HR questions in their native languages (Albanian SQ, Serbian SR, English EN). HR documents are scattered across SharePoint (Albania) and a ZIP archive (Serbia). This assistant provides a grounded, multilingual chatbot that:

- Answers **only** from official HR documents indexed per office
- Enforces strict **office isolation** (Albania users see only Albania documents; Serbia users see only Serbia documents)
- Operates in three languages: EN, SR (Serbian with Cyrillic/Latin), SQ (Albanian)
- Refuses off-topic questions before calling the LLM
- Cites every claim with document name, date, and page/slide number
- Logs all queries to an audit trail for compliance

---

## 2. Architecture Overview

```
SOURCES (tagged by source, not by language or filename)
┌──────────────────────────────┬──────────────────────────────────┐
│ Albania → SharePoint         │ Serbia → ZIP / local folder      │
│ (Microsoft Graph REST API)   │ (~24 PDFs, ~660 chunks indexed)  │
└──────────────────────────────┴──────────────────────────────────┘
    │ SourceService                                    │
    ▼                                                  ▼
DocumentLoader (Apache Tika 2.9.2)
  PDF → text + page numbers, PPTX → text + slide numbers
    │
Indexer (chunk → embed → upsert)
  800-char sliding window, 100-char overlap
  Chunk ID: name:lastModified:chunkIndex  (idempotent)
    │
VoyageEmbeddingClient → voyage-3 (1024-dim, multilingual)
    │
PostgreSQL 17 + pgvector
  chunks table: id, content, office, source_name, source_url,
                last_modified, page, file_type, language, vector(1024)
  Indexes: B-tree on office + IVFFlat cosine on vector
    │
    ├── ScopeGuard (cosine pre-check, threshold=0.10)
    │
    └── HrAgentService (Claude Opus 4.8 tool-use loop, max 5 iterations)
            ├── Tool: searchHrDocuments   (office bound server-side)
            └── Tool: getDocumentMetadata
    │
ChatController (Spring Boot REST, POST /api/chat)
    │
React + Vite SPA (chat UI, citations panel, office badge)
```

---

## 3. Agent Design

### 3.1 Single-Agent Decision

One Claude Opus 4.8 agent handles all HR topics and both offices. We do **not** route to separate specialist agents by topic (leave/benefits/IT) or by office.

Reasons:
- **Real isolation is structural, not model-level**: Office isolation is enforced by a mandatory `WHERE office = ?` SQL predicate applied before cosine scoring. Even if Claude were prompted to access another office's data, the tool implementation makes it physically impossible.
- **No accuracy gain from specialization**: The vector search already surfaces topic-relevant chunks. A topic-routing classifier would add latency and a failure mode without improving retrieval precision.
- **Simpler failure modes**: One agent means one prompt to debug, one tool-use loop to monitor, one set of system prompt constraints to verify.

### 3.2 System Prompt

The system prompt is constructed at **runtime** by inserting the authenticated user's `{office}` (from session) and the auto-detected `{language}` (from `LanguageDetector`):

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

**Variable substitutions at runtime:**

| Variable | Source | Example |
|----------|--------|---------|
| `{office}` | `session.getAttribute("office")` | `"serbia"` |
| `{language}` | `LanguageDetector.detect(question)` | `"sr"` |

### 3.3 Tool-Use Protocol

The agent loop runs for up to **5 iterations**. The key invariant: **iteration 0 always has `tool_choice: {type: "any"}`**, which forces Claude to call a tool before generating any text. This prevents Claude from skipping retrieval and responding from its training data.

```
Iteration 0:
  REQUEST: {messages, tools, tool_choice: {type: "any"}}
  RESPONSE: stop_reason = "tool_use"  (guaranteed by tool_choice)
  ACTION: execute tools, append results to messages

Iteration 1 (and beyond):
  REQUEST: {messages, tools}  (no tool_choice — Claude decides)
  RESPONSE: stop_reason = "end_turn"  → extract finalText, done
         OR stop_reason = "tool_use"  → execute tools, loop

Typical pattern: 2 iterations (tool_use → end_turn)
Complex pattern: 3-4 iterations (multiple searches or metadata lookups)
Failure: 5 iterations exhausted → return "unable to complete" message
```

---

## 4. Tool Schemas

### 4.1 `searchHrDocuments`

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

**Server-side implementation:**
1. Embed `query` via Voyage-3 → `float[1024]`
2. `SELECT ... FROM chunks WHERE office = ? ORDER BY vector <=> ? LIMIT 5`
3. Format each chunk as `[sourceName | lastModified | Page N]\ncontent\n\n`
4. Collect unique citations (sourceName + page)
5. Return formatted string as `tool_result`

**Returns:** String containing up to 5 excerpts, each with `[source | date | page]` header, followed by chunk content.

**Example interaction:**

User question: "Šta znate o RAS-u?" (Serbian: "What do you know about RAS?")

Claude's tool call:
```json
{"name": "searchHrDocuments", "input": {"query": "RAS sistem izveštaj"}}
```

Tool result:
```
[Procedure - Cesto postavljena pitanja - Frequently Asked Questions.pdf | 2026-06-18T... | N/A]
1. Šta je RAS?
RAS je Interni administrativni sistem naše kompanije koji prati radne aktivnosti i odsustva
svih ESL zaposlenih. RAS se popunjava jednom mesečno...
```

Claude's final response (in Serbian, as detected):
```
RAS (Rekorder Aktivnosti i Sati) je interni administrativni sistem kompanije koji prati
radne aktivnosti i odsustva zaposlenih. Prema dokumentu "Često postavljana pitanja" (datum: ...),
RAS se popunjava jednom mesečno, okvirno sredinom tekućeg meseca.
```

### 4.2 `getDocumentMetadata`

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

**Returns:** `"sourceName | Last modified: ISO-8601 | URL: path/or/sharepoint-url"`

---

## 5. Retrieval Strategy

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Chunk size | ~800 chars | Fits a single policy clause or procedure step |
| Chunk overlap | ~100 chars | Preserves sentence boundaries across chunks |
| Top-k per search | 5 | Enough context for synthesis; fits in token budget |
| Similarity metric | Cosine (pgvector `<=>`) | Standard for normalized embedding vectors |
| Score formula | `1 - cosine_distance` | 1.0 = identical, 0.0 = orthogonal |
| ScopeGuard k | 1 | Fast pre-check; only need the best match to test relevance |
| Office filter | `WHERE office = ?` (SQL) | Applied before vector scan; never bypassed |
| ANN index | IVFFlat, lists=100 | Effective for 500–50,000 rows; rebuild after major re-ingestion |

**Idempotent re-indexing:**
Chunk ID = `filename + ":" + lastModified + ":" + chunkIndex`. On re-ingestion:
- If `lastModified` hasn't changed → chunk ID matches existing row → `ON CONFLICT DO UPDATE` is a no-op
- If `lastModified` changed → new chunk ID → full re-embed of that document only
- Other documents are untouched regardless of run order

---

## 6. Language Handling

| Step | Mechanism | Effect |
|------|-----------|--------|
| Query language detection | Apache Tika `OptimaizeLangDetector` → keyword fallback | Determines reply language |
| Reply language | System prompt: `Reply ONLY in {language}` | Claude responds in detected language |
| Search language | Tool description guides keyword extraction | Claude uses topic keywords (any language) |
| Document language | Stored in `language` column; NOT used for filtering | Informational only |
| Office assignment | From authenticated session only | Unaffected by any language |

Language detection is **never** used to route queries to a different office or document set. An Albanian-speaking user at the Serbia office (if such existed) would receive Serbia documents, answered in Albanian.

**Supported languages:**
- `en` — English (default)
- `sr` — Serbian (Latin + Cyrillic, detected by keyword list)
- `sq` — Albanian (detected by keyword list)

---

## 7. Security and Access Control

### 7.1 Three Independent Layers

**Layer 1 — Session office binding**
```
POST /api/login → IdentityService.resolveOffice(profile)
  company.displayName ∋ "Albania"  → office = "albania"
  company.displayName ∋ "Serbia"   → office = "serbia"
  city = Tirana                    → office = "albania" (fallback)
  city = Beograd|Belgrade          → office = "serbia"  (fallback)
  unresolvable                     → 403 Forbidden
→ session.setAttribute("office", resolvedOffice)
```

`ChatController` reads `office` from the session only:
```java
String office = (String) session.getAttribute("office");
if (office == null) return ResponseEntity.status(401).build();
```

Office is **never** accepted from the request body, request parameters, or the model's output.

**Layer 2 — SQL predicate (structural)**
```sql
WHERE office = ?   -- bound to session office, not model-supplied
```
This predicate is applied before the IVFFlat cosine scan. Other-office documents are never in the candidate set. No prompt injection or tool manipulation can bypass a SQL WHERE clause.

**Layer 3 — LLM grounding (behavioral)**
System prompt prohibits cross-office speculation and non-HR answers. This defends against edge cases the SQL layer cannot cover (e.g., queries that are semantically on-topic but seek office-to-office comparisons).

### 7.2 Authentication Modes

| Mode | `AUTH_MODE` | Mechanism |
|------|-------------|-----------|
| Demo | `mock` | JSON file from `data/mock_profiles/{username}.json` |
| Production | `entra` | Microsoft Entra ID via MSAL4J; `GET /me/profile` from Graph API |

Both modes run through the same `IdentityService` validation (including `userPersona == "internalMember"` check and office resolution). The mock profiles mirror the Graph API JSON shape exactly, so the production code path is tested with mock data.

### 7.3 Access Denied Scenarios

| Scenario | HTTP status | Mechanism |
|----------|-------------|-----------|
| No session | 401 | Spring Security |
| Session exists but no office attribute | 401 | `ChatController` explicit check |
| Office cannot be resolved | 403 | `IdentityService` throws; `AuthController` catches |
| User is not `internalMember` | 403 | `IdentityService` validates `userPersona` field |
| Query is off-topic (score < 0.10) | 200 with `refused: true` | ScopeGuard |

---

## 8. Multi-Turn Conversation

The frontend sends conversation history with each request:

```json
{
  "question": "And what about parental leave?",
  "history": [
    {"role": "user",      "content": "How many vacation days do I get?"},
    {"role": "assistant", "content": "According to the HR policy document..."}
  ]
}
```

The backend caps history at **20 entries (10 full turns)** and prepends them to the messages list before the current question. This gives Claude context for follow-up questions ("And what about..." / "How does that compare...") without unbounded context growth.

Tool results from prior turns are **not** included in history — only the user/assistant text pairs. Each new question triggers a fresh tool-use loop. This is intentional: tool results can be large and stale, so it's better for Claude to re-search if the conversation shifts topic.

---

## 9. Fallback Strategy

| Scenario | Fallback |
|----------|----------|
| `VOYAGE_API_KEY` not set | Switch to `EMBEDDING_PROVIDER=djl` (local 384-dim model); lower recall quality |
| Voyage 429 rate limit | Exponential backoff: 22s initial, ×2 per retry, max 120s, 6 retries |
| SharePoint Graph auth fails (Albania) | Set `ALB_SOURCE=folder` + `ALB_FOLDER_PATH=/path/to/docs` |
| Albania source = SharePoint (default) | Fallback to `ALB_SOURCE=local` with a local ZIP |
| Serbia source = ZIP (default) | Fallback to `ALB_SOURCE=folder` with extracted directory |
| Vector index empty at chat time | ScopeGuard bypasses threshold check; agent still runs |
| Claude tool-use loop exhausted (5 iters) | Returns "unable to complete" message; audited as `refused` |
| Scanned/image-only PDF (Tika empty text) | Skip with WARN log; file not indexed |
| `ANTHROPIC_API_KEY` not set | Returns `[CONFIG]` error message immediately; audited |

---

## 10. Document Versioning and Re-Indexing

| Scenario | Action |
|----------|--------|
| Document unchanged | `ON CONFLICT DO UPDATE` no-op (chunk ID unchanged) |
| Document updated (new `lastModified`) | New chunk IDs created; old chunks remain (orphaned) |
| New document added to source | New chunk IDs created; indexed on next `--ingest` run |
| Document deleted from source | Old chunks remain in DB; manually delete or do `--ingest=all` after clearing |
| Full re-index | Run `--ingest=all`; existing chunks with same IDs are overwritten safely |

---

## 11. Model and Embedding Choice

| Component | Choice | Version | Rationale |
|-----------|--------|---------|-----------|
| LLM | Claude Opus 4.8 | `claude-opus-4-8` | Best tool-use reliability; best multilingual instruction following |
| LLM client | `java.net.http.HttpClient` | Java 11+ built-in | No external SDK; works on corporate Maven mirrors |
| Embeddings (primary) | Voyage AI `voyage-3` | `voyage-3` | Top multilingual retrieval; 1024-dim shared EN/SR/SQ space |
| Embeddings (fallback) | DJL + HuggingFace | `paraphrase-multilingual-MiniLM-L12-v2` | Fully local; no API key needed; 384-dim |
| Document parsing | Apache Tika | 2.9.2 | Single dependency handles PDF + PPTX + language detection |
| Vector DB | PostgreSQL 17 + pgvector | pgvector 0.7+ | Team-operated PostgreSQL; ACID; IVFFlat ANN index |
| Auth | Spring Security sessions | 3.3.5 | Session binding keeps office on server; no JWT office field |
