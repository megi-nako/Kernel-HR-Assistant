# Kernel HR Assistant — Technical Architecture

## 1. System Overview

The Kernel HR Assistant is a **production-grade multilingual RAG chatbot** that answers HR questions grounded exclusively in official company documents. It enforces strict per-office data isolation and operates in three languages (EN/SR/SQ) without any model fine-tuning.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              DOCUMENT SOURCES                                   │
│                                                                                 │
│  Albania Office                          Serbia Office                          │
│  Microsoft SharePoint                    Local ZIP archive                      │
│  (Graph REST API / MSAL4J)               (SRB_ZIP_PATH → _srb_extracted)       │
│  PDF, PPTX files                         PDF, PPTX files (~24 docs)            │
└───────────────────────────┬─────────────────────────┬──────────────────────────┘
                            │                         │
                            ▼ SourceService           ▼
                  ┌─────────────────────────────────────────┐
                  │           INGESTION PIPELINE            │
                  │                                         │
                  │  DocumentLoader (Apache Tika 2.9.2)    │
                  │  • PDF  → text + page numbers           │
                  │  • PPTX → text + slide numbers          │
                  │  • Skips scanned/image-only pages       │
                  │                                         │
                  │  Indexer                                │
                  │  • Chunk: ~800 chars / 100 overlap      │
                  │  • ID: name:lastModified:chunkIndex     │
                  │  • Idempotent: skips unchanged chunks   │
                  └──────────────────┬──────────────────────┘
                                     │
                                     ▼ embedAll(batch)
                  ┌─────────────────────────────────────────┐
                  │        VOYAGE AI EMBEDDING              │
                  │        model: voyage-3                  │
                  │        1024 dimensions                  │
                  │        Multilingual (EN / SR / SQ)      │
                  │        Batch size: 8, 21s inter-batch   │
                  └──────────────────┬──────────────────────┘
                                     │
                                     ▼ upsert(chunks)
                  ┌─────────────────────────────────────────┐
                  │         POSTGRESQL 17 + PGVECTOR        │
                  │                                         │
                  │  chunks table:                          │
                  │  id, content, office, source_name,      │
                  │  source_url, last_modified, page,       │
                  │  file_type, language, vector(1024)      │
                  │                                         │
                  │  Indexes:                               │
                  │  • B-tree  idx_chunks_office            │
                  │  • IVFFlat idx_chunks_vector_cosine     │
                  │    (lists=100, vector_cosine_ops)       │
                  └─────────────────────────────────────────┘
                                     │
                  ┌──────────────────┴──────────────────────┐
                  │                QUERY PATH               │
                  │                                         │
                  │  POST /api/chat  {question, history?}   │
                  │                                         │
                  │  1. Session auth  → office (immutable)  │
                  │  2. LanguageDetector → lang (en/sr/sq)  │
                  │  3. ScopeGuard:                         │
                  │     embed(question) → top-1 search      │
                  │     score < 0.10 → refuse (no LLM call) │
                  │  4. HrAgentService → Claude Opus 4.8   │
                  │     system prompt + tool definitions    │
                  │     tool_choice: {type: "any"}          │
                  │     → searchHrDocuments(keyword query)  │
                  │     → VectorStore.search(vec, office,5) │
                  │     → Claude generates grounded answer  │
                  │  5. AuditService → audit.log            │
                  └──────────────────┬──────────────────────┘
                                     │
                                     ▼
                  ┌─────────────────────────────────────────┐
                  │              FRONTEND (React/Vite)      │
                  │                                         │
                  │  • Login (mock JSON / Entra SSO)        │
                  │  • Chat view with typing indicator      │
                  │  • Citations panel (source + page)      │
                  │  • Office badge (immutable, from session)│
                  │  • Suggestion cards per HR topic        │
                  └─────────────────────────────────────────┘
```

---

## 2. Component Inventory

### 2.1 Backend (Spring Boot 3.3.5 / Java 17)

| Package | Class | Role |
|---------|-------|------|
| `agent` | `HrAgentService` | Orchestrates Claude tool-use loop (up to 5 iterations) |
| `agent` | `HrTools` | Defines `searchHrDocuments` and `getDocumentMetadata`; executes tool calls |
| `agent` | `ScopeGuard` | Pre-LLM gate: embeds query, checks cosine score against threshold |
| `auth` | `IdentityService` | Resolves office from Microsoft Graph profile; validates internalMember |
| `audit` | `AuditService` | Appends every query+response to `data/audit.log` (compliance) |
| `embedding` | `VoyageEmbeddingClient` | Calls `voyage-3` REST API; handles 3 RPM rate limit with retry + batching |
| `embedding` | `StubEmbeddingClient` | Zero-vector fallback for unit tests; never used in production |
| `ingestion` | `DocumentLoader` | Apache Tika: PDF → per-page text, PPTX → per-slide text |
| `ingestion` | `Indexer` | Sliding-window chunker → embed → upsert; idempotent by chunk ID |
| `ingestion` | `IngestionRunner` | `ApplicationRunner`: `--ingest=serbia\|albania\|all` |
| `ingestion` | `ZipSource` | Extracts Serbia ZIP, tags `office=serbia` from source |
| `ingestion` | `LocalFolderSource` | Walks a local directory; source-tagged office |
| `ingestion` | `SharePointClient` | Microsoft Graph `GET /drives/{id}/root/children` → download |
| `retrieval` | `RetrieverService` | Embeds query → `VectorStore.search(vector, office, k)` |
| `store` | `VectorStore` | JDBC: `upsert` (ON CONFLICT), `search` (cosine, office-filtered) |
| `util` | `LanguageDetector` | Tika `LanguageDetector` → keyword fallback for en/sr/sq |
| `web` | `ChatController` | `POST /api/chat`: validates session, detects language, calls agent |
| `web` | `AuthController` | `POST /api/login`, `POST /api/logout`, `GET /api/users` |
| `web` | `StatusController` | `GET /api/status`: office, docCount, lastIndexed |
| `config` | `AppProperties` | `@ConfigurationProperties(prefix = "kernel")`: all config in one place |
| `config` | `SecurityConfig` | Session-based Spring Security; 401 on unauthenticated |

### 2.2 Frontend (React 18 + Vite)

| Component | Purpose |
|-----------|---------|
| `LoginScreen` | Mock user picker → `POST /api/login` → session cookie |
| `ChatLayout` | Layout shell: sidebar + chat view |
| `ChatView` | Message list, typing indicator, suggestion cards, composer |
| `Citations` | Collapsible source panel per AI message |
| `Sidebar` | Conversation history, user profile, office badge |
| `SessionContext` | React context: user, office, chat state |
| `api.js` | `fetch` wrapper for all backend calls (cookie-based auth) |

---

## 3. Ingestion Pipeline (Data Flow)

```
  SourceService.iterDocuments("serbia")
       │
       ├── ZipSource.loadAll()
       │     Extract ZIP to _srb_extracted/
       │     Walk .pdf / .pptx files
       │     Build SourceDoc{bytes, name, sourceUrl, lastModified,
       │                      office="serbia", fileType}
       │
       ▼
  Indexer.index(List<SourceDoc>)
       │
       ├── DocumentLoader.parse(src)          Apache Tika
       │     PDF:  AutoDetectParser → BodyContentHandler(-1) → per-page text
       │     PPTX: AutoDetectParser → per-slide text
       │     Language: OptimaizeLangDetector (fallback: keyword detection)
       │
       ├── chunkText(text, size=800, overlap=100)
       │     sliding window → List<String> chunks
       │
       ├── For each chunk:
       │     id = srcName + ":" + lastModified + ":" + chunkIndex
       │     Skip if VectorStore.hasChunk(id)  ← idempotent
       │
       ├── EmbeddingClient.embedAll(newChunkTexts)
       │     Voyage AI POST /v1/embeddings
       │     model=voyage-3, input=[chunk0, chunk1, ...]
       │     Returns float[1024][] per chunk
       │
       └── VectorStore.upsert(chunksWithVectors)
             JDBC: INSERT ... ON CONFLICT (id) DO UPDATE
```

---

## 4. Query Pipeline (Data Flow)

```
  POST /api/chat  {"question": "what is RAS?", "history": [...]}
       │
  ChatController
       ├── session.getAttribute("office")  — NEVER from request body
       ├── session.getAttribute("upn")
       ├── LanguageDetector.detect(question) → "en" | "sr" | "sq"
       │
  ScopeGuard.check(question, office)           ← Pre-LLM gate
       ├── VectorStore.countByOffice(office)   — bypass if empty (dev)
       ├── RetrieverService.retrieve(question, office, k=1)
       │     VoyageEmbeddingClient.embed(question) → float[1024]
       │     VectorStore.search(vector, office, 1)
       │       SQL: SELECT ... WHERE office = ? ORDER BY vector <=> ? LIMIT 1
       │     score = 1 - cosine_distance
       ├── score >= 0.10 → proceed
       └── score <  0.10 → ChatResponse{refused=true, reason="I can only help..."}
       │
  HrAgentService.answer(question, language, office, upn, history)
       │
       ├── Build systemPrompt:
       │     "You are the Kernel HR Assistant for {office} office.
       │      Reply ONLY in {language}.
       │      ALWAYS call searchHrDocuments first.
       │      Use concise keywords for queries, not full sentences.
       │      ..."
       │
       ├── Build messages: [history (capped at 20 / 10 turns)] + [{role:user, content:question}]
       │
       ├── LOOP (max 5 iterations):
       │     │
       │     ├── iteration == 0:  tool_choice = {type: "any"}   ← FORCES tool call
       │     │   iteration >  0:  no tool_choice               ← Claude decides
       │     │
       │     ├── POST https://api.anthropic.com/v1/messages
       │     │   {model, max_tokens=4096, system, messages, tools, tool_choice?}
       │     │
       │     ├── stop_reason == "end_turn"
       │     │     Extract text blocks → finalText; break
       │     │
       │     └── stop_reason == "tool_use"
       │           For each tool_use block:
       │             searchHrDocuments({query: "RAS system"})
       │               RetrieverService.retrieve("RAS system", office, k=5)
       │               Format: [sourceName | lastModified | Page N]\ncontent\n\n
       │               Populate citations[]
       │             getDocumentMetadata({sourceName: "..."})
       │               RetrieverService.retrieve(sourceName, office, 3)
       │               Filter by exact name match
       │           messages += [{role:assistant, content:[tool_use blocks]}]
       │           messages += [{role:user,      content:[tool_result blocks]}]
       │
       └── AuditService.log(upn, office, question, answered, finalText)
       │
  ChatResponse{text, language, citations[], refused, reason}
```

---

## 5. Security Architecture

Three independent enforcement layers. Bypassing one does not bypass the others.

### Layer 1 — Session-Based Office Isolation

```
Login:
  POST /api/login {"username": "user@eng.it"}
       │
  IdentityService.resolveOffice(profile)
       company.displayName ∋ "Albania" → "albania"
       company.displayName ∋ "Serbia"  → "serbia"
       city ∋ "Tirana"                 → "albania"  (fallback)
       city ∋ "Beograd|Belgrade"       → "serbia"   (fallback)
       no match                        → 403 Forbidden
       │
  HTTP session: setAttribute("office", resolvedOffice)
               setAttribute("upn", user.upn)

Query:
  ChatController reads office from session ONLY.
  Request body has no office field.
  Office is never derived from the question text.
```

### Layer 2 — SQL-Level Office Filter

```sql
-- Applied BEFORE cosine similarity scoring
SELECT id, content, ..., 1 - (vector <=> ?) AS score
FROM   chunks
WHERE  office = ?          -- session office — immutable predicate
  AND  vector IS NOT NULL
ORDER  BY vector <=> ?
LIMIT  ?
```

The `WHERE office = ?` predicate is applied before the IVFFlat index scan. Other-office rows are never part of the candidate set. The model cannot specify the office — it is not a tool parameter.

### Layer 3 — System Prompt Grounding

```
REFUSE non-HR questions.
NEVER use your own prior knowledge.
Answer ONLY from retrieved document excerpts.
NEVER mention or speculate about the other office.
```

If a query somehow passes the scope gate (Layer 1/ScopeGuard) but has no relevant HR content in the retrieved chunks, Claude returns the canned "not found" message rather than hallucinating.

### Authentication

| Mode | Mechanism |
|------|-----------|
| `mock` | JSON file from `data/mock_profiles/{username}.json` (mirrors Graph API shape) |
| `entra` | Microsoft MSAL4J client-credentials → Graph `/me/profile` |

Both modes run through the same `IdentityService.loadUser()` and `resolveOffice()` code. Mock mode is for demos; Entra mode is the production path.

---

## 6. Database Schema

```sql
CREATE TABLE chunks (
    id            VARCHAR(512)  PRIMARY KEY,   -- "name:lastModified:chunkIndex"
    content       TEXT          NOT NULL,
    office        VARCHAR(50)   NOT NULL,       -- "serbia" | "albania"
    source_name   VARCHAR(512),                 -- original filename
    source_url    TEXT,                         -- local path or SharePoint URL
    last_modified VARCHAR(64),                  -- ISO-8601 — drives idempotent re-index
    page          INTEGER,                      -- PDF page or PPTX slide number
    file_type     VARCHAR(10),                  -- "pdf" | "pptx"
    language      VARCHAR(10),                  -- "en" | "sr" | "sq"
    vector        vector(1024)                  -- Voyage-3 embedding
);

CREATE INDEX idx_chunks_office ON chunks (office);
CREATE INDEX idx_chunks_vector_cosine
    ON chunks USING ivfflat (vector vector_cosine_ops)
    WITH (lists = 100);
```

**Key design choices:**
- `office` indexed with B-tree: hard partition filter before vector scan
- IVFFlat with `lists=100`: effective ANN for 500–50,000 rows per office
- Chunk ID encodes `lastModified`: re-indexing a changed doc creates new IDs, old ones remain (no orphan cleanup needed for demos; a full `--ingest=all` is idempotent)
- `ON CONFLICT (id) DO UPDATE`: upsert means partial re-index (only changed documents) is safe

---

## 7. Technology Choices and Rationale

| Decision | Choice | Rationale |
|----------|--------|-----------|
| LLM | Claude Opus 4.8 | Best-in-class tool use reliability; supports multi-step tool loops |
| LLM client | Raw `java.net.http.HttpClient` | No SDK dependency; works on corporate Nexus; same API surface |
| Embeddings | Voyage AI `voyage-3` (1024-dim) | Explicitly multilingual; top retrieval benchmark for EN+non-English pairs |
| Embedding fallback | DJL `paraphrase-multilingual-MiniLM-L12-v2` | Fully local, no API key, offline demos |
| Vector DB | PostgreSQL 17 + pgvector | Team already operates PostgreSQL; one less infrastructure component; ACID properties for audit |
| Document parsing | Apache Tika 2.9.2 | Handles PDF (PDFBox) + PPTX (POI); single dependency; language detection built-in |
| Auth | Spring Security sessions | Stateful sessions keep office binding server-side; no JWT office field that could be forged |
| SharePoint | Microsoft Graph SDK 6.4.0 + MSAL4J 1.17.2 | Official Microsoft library; handles token refresh automatically |
| Chunking | 800-char sliding window / 100-char overlap | Balances context (enough for a single policy clause) vs. precision (short chunks rank better) |

---

## 8. API Contract

```
POST /api/login
  Request:  {"username": "string"}
  Response: {"upn": "string", "displayName": "string", "office": "serbia|albania"}
  Error:    401 if user not found; 403 if not internalMember or office unresolvable

POST /api/chat
  Requires: valid session cookie (JSESSIONID)
  Request:  {"question": "string", "history": [{"role": "user|assistant", "content": "string"}]?}
  Response: {
    "text":      "string",          -- grounded answer (empty if refused)
    "language":  "en|sr|sq",        -- detected query language
    "citations": [{
      "sourceName":    "string",
      "lastModified":  "ISO-8601",
      "url":           "string",
      "page":          integer|null
    }],
    "refused":   boolean,
    "reason":    "string|null"      -- refusal message if refused=true
  }

GET /api/status
  Response: {"office": "string", "docCount": int, "lastIndexed": "ISO-8601|null"}

GET /api/users
  Response: ["username1", "username2"]  -- lists available mock profiles

POST /api/logout
  Invalidates session; returns 200
```

---

## 9. Configuration Reference

```yaml
kernel:
  anthropic:
    api-key:  ${ANTHROPIC_API_KEY}               # required
    model:    ${CLAUDE_MODEL:claude-opus-4-8}
  embedding:
    provider:       ${EMBEDDING_PROVIDER:voyage}  # voyage | djl
    voyage-api-key: ${VOYAGE_API_KEY}             # required if provider=voyage
    model:          ${EMBEDDING_MODEL:voyage-3}
  srb:
    source:      ${SRB_SOURCE:zip}                # zip | folder
    zip-path:    ${SRB_ZIP_PATH:./data/srb_docs.zip}
    extract-dir: ${SRB_EXTRACT_DIR:./data/_srb_extracted}
    folder-path: ${SRB_FOLDER_PATH:}
  alb:
    source:      ${ALB_SOURCE:sharepoint}         # sharepoint | local | folder
    zip-path:    ${ALB_ZIP_PATH:}
    folder-path: ${ALB_FOLDER_PATH:}
  auth:
    mode:             ${AUTH_MODE:mock}            # mock | entra
    mock-profile-path: ${MOCK_PROFILE_PATH:./data/mock_profiles/}
  agent:
    scope-threshold:  ${SCOPE_THRESHOLD:0.10}     # cosine similarity gate
  index:
    path: ${INDEX_PATH:./data/index.json}
```

---

## 10. Deployment Runbook

```bash
# 1. Start database
docker compose up -d

# 2. Set environment (minimum required)
export ANTHROPIC_API_KEY=sk-ant-...
export VOYAGE_API_KEY=pa-...
export DATABASE_USER=postgres
export DATABASE_PASSWORD=admin

# 3. Index documents (one-time, persists in PostgreSQL)
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments=--ingest=all

# 4. Start backend (in normal mode, no --ingest flag)
mvn spring-boot:run
# → http://localhost:8080

# 5. Start frontend
cd ../frontend
npm install && npm run dev
# → http://localhost:5173
```
