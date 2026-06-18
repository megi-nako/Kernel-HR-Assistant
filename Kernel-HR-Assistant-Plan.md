# Kernel HR Assistant — Hackathon Build Plan

## Context

Team E "Kernel" (Megi Nako, Agathi Drago, Urim Toshi) is competing in the ALB Engineering Hackathon 2026. The goal is to build a multilingual HR chatbot that answers questions in EN/SR/SQ, grounded in HR PDF/PPTX documents stored on SharePoint (Albania) and a configurable ZIP (Serbia). **Deadline: 22:00 today.**

Judging weights:

- 40% Usable, production-ready solution
- 20% Quality of specification for agents
- 15% Utilization of LLMs
- 15% Technical approach & architecture
- 10% Agent setup

**Stack confirmed:** Java 17+ / Spring Boot (backend) · Angular (frontend) · Anthropic Claude (Opus 4.8, official Java SDK) · Voyage AI embeddings · in-memory vector store · Microsoft Graph API · Maven · Node.js

> **Stack change — Java/Angular (no Python installed):** The original plan was Python/Streamlit; we
> are switching because Python is not available on our machines.
> - **Backend:** Java + **Spring Boot** REST API.
> - **Frontend:** **Angular** SPA calling the REST API.
> - **LLM:** **Claude Opus 4.8** via the **official Anthropic Java SDK** (`com.anthropic:anthropic-java`), using tool use for retrieval.
> - **Embeddings:** Anthropic has no embeddings endpoint, and `sentence-transformers` is Python-only.
>   Primary = **Voyage AI** REST (`voyage-3`, multilingual; needs `VOYAGE_API_KEY`). Fully-local
>   fallback = **DJL** (Deep Java Library) running a HuggingFace multilingual model in the JVM (no key, heavier).
> - **Vector store:** **in-memory cosine store persisted to a JSON file** (no DB server needed). Alt: Qdrant in Docker.
> - **Document parsing:** **Apache Tika** (wraps PDFBox for PDF + POI for PPTX) — one dependency for both formats + language detection.
> - We use a **single** Claude agent with tool use, not multiple agents (see *Agent design decision*).

> **Two prerequisites to confirm in the first 15 min:** (1) a working `ANTHROPIC_API_KEY`, and
> (2) **Node.js / Angular CLI installed** (Angular needs Node — same "is it installed?" gotcha that
> moved us off Python). If no Voyage key is obtainable, switch embeddings to the DJL local fallback.

**GitHub delivery (required):**

- Push all work to the team repo before **22:00** — commits after 22:00 do not count
- Add reviewers on GitHub: **vstanojevic** and **marest94**
- Include **SPEC.md** in the repo (20% of score)
- Keep a clear commit history for evaluation

---

## Architecture Overview

```
  SOURCES (per office, tagged at ingestion — office is NOT content-derived)
  ┌─────────────────────────────────┬─────────────────────────────────┐
  │ Albania → SharePoint (PDF/PPTX)  │ Serbia → ZIP at $SRB_ZIP_PATH    │
  │ (Graph Java SDK) ·· local zip fb │ (configurable path) · PDF/PPTX   │
  └─────────────────────────────────┴─────────────────────────────────┘
      │
      ▼ SourceService: {sharepoint | zip | localFolder}
  Ingestion (Spring Boot)
  (DocumentLoader = Apache Tika → text; Indexer = chunk + embed)
      │  tags every chunk with office = serbia | albania (from SOURCE, not language)
      ▼ Voyage AI embeddings (multilingual EN/SR/SQ) — embed once, persist
  In-memory Vector Store (persisted to data/index.json) — the "context in DB"; incremental upsert per doc
      │
      ▼ cosine search  ── MANDATORY office filter (predicate before similarity) ──
  Single Claude agent (Anthropic Java SDK, tool use — NOT multi-agent)
  ├── Tool: searchHrDocuments   (office injected server-side, not a tool arg)
  └── Tool: getDocumentMetadata
      │  scope gate: if no grounded chunk → refuse (HR-only)
      ▼ Claude Opus 4.8 (claude-opus-4-8) — grounded answer from retrieved chunks only
  Grounded answer (same language as question, only from this office's docs)
      │
      ▼ Spring Boot REST API  (POST /api/chat — office read from SERVER SESSION, never the client)
      ▼
  Angular SPA  (auth guard — internal employees only)
  (chat + source citations + detected-language badge; office shown, NOT selectable)
```

> **Key principle:** `office` is derived from the **authenticated user's identity** (Graph profile),
> stored in the **server-side session**, and applied as a **mandatory** filter in the retriever. The
> Angular client never sends `office` and there is no office selector. This single mechanism enforces
> both office isolation (#1) and internal-only access (#3). See **Security & Access Restrictions**.

---

## File Structure

```
Kernel-HR-Assistant/
├── README.md                       # setup + demo instructions
├── SPEC.md                         # Agent specification doc (required for judging)
├── .gitignore                      # target/, node_modules/, data/index.json, .env, *.local
│
├── backend/                        # Spring Boot (Maven)
│   ├── pom.xml                     # deps: spring-boot-starter-web/-security, anthropic-java,
│   │                               #       tika-core+tika-parsers, microsoft-graph + msal4j, jackson
│   ├── src/main/resources/
│   │   └── application.yml          # config via env vars (no secrets committed)
│   └── src/main/java/com/kernel/hr/
│       ├── HrAssistantApplication.java
│       ├── config/                 # AppProperties, SecurityConfig (session gate), CorsConfig (allow ng :4200)
│       ├── auth/
│       │   ├── IdentityService.java     # resolve Graph profile → office; internal-only gate (#3)
│       │   └── AuthController.java      # POST /api/login (mock) / SSO later; sets server session
│       ├── ingestion/
│       │   ├── SourceService.java       # abstraction: sharepoint | zip | localFolder
│       │   ├── ZipSource.java           # read+extract configurable SRB_ZIP_PATH (Serbia); office=serbia
│       │   ├── SharePointClient.java    # Graph Java SDK + MSAL4J: list + download PDF/PPTX (Albania)
│       │   ├── DocumentLoader.java      # Apache Tika: PDF + PPTX → text (+ page/slide where available)
│       │   ├── Indexer.java             # chunk + embed + upsert to vector store (tags office, fileType)
│       │   └── IngestionRunner.java     # ApplicationRunner: re-index on `--ingest=serbia|albania|all`
│       ├── embedding/
│       │   └── EmbeddingClient.java     # Voyage REST (primary) | DjlEmbeddingModel (local fallback)
│       ├── store/
│       │   ├── Chunk.java               # record(content, office, sourceName, sourceUrl, lastModified, page, fileType, language, float[] vector)
│       │   └── VectorStore.java         # in-memory cosine search + JSON persist; MANDATORY office filter
│       ├── retrieval/
│       │   └── RetrieverService.java    # retrieve(query, office, k) — office required (#1)
│       ├── agent/
│       │   ├── HrTools.java             # tool defs (office bound server-side, not a tool arg)
│       │   ├── ScopeGuard.java          # HR-scope grounding gate (#2)
│       │   └── HrAgentService.java      # single Claude tool-use loop + system prompt
│       ├── web/
│       │   ├── ChatController.java      # POST /api/chat (office from session, NOT request body)
│       │   ├── StatusController.java    # GET /api/status (doc count per office, last indexed)
│       │   └── dto/                     # ChatRequest, ChatResponse, Citation, LoginResponse, StatusResponse
│       ├── audit/
│       │   └── AuditService.java        # append-only log: ts, user, office, question, answered/refused
│       └── util/
│           └── LanguageDetector.java    # Tika/Lingua — sets REPLY language only (not access)
│
├── frontend/                       # Angular
│   └── src/app/
│       ├── app.component.ts / app.routes.ts
│       ├── guards/auth.guard.ts          # blocks routes until logged in (#3, UX side)
│       ├── services/
│       │   ├── api.service.ts             # HttpClient → /api/*
│       │   ├── session.service.ts         # holds {user, office, messages, lang}
│       │   └── mock-api.service.ts        # canned responses — frontend runs before backend exists
│       └── components/
│           ├── login/                     # mock-profile picker → POST /api/login (Urim)
│           ├── sidebar/                   # office badge (read-only), index status (Urim)
│           ├── chat/                      # message history, input, calls api.chat (Loerti)
│           └── citation/                  # citation expander: name, date, link, page/slide (Loerti)
│
└── data/
    ├── mock_profiles/              # Graph /me/profile JSON per user (dev login)
    └── index.json                  # persisted vector store (gitignored)
```

---

## Implementation Steps

### Step 1 — Project skeleton & dependencies

**Backend (`backend/pom.xml`):**
- `spring-boot-starter-web`, `spring-boot-starter-security` (session gate)
- `com.anthropic:anthropic-java` (official Claude SDK)
- `org.apache.tika:tika-core` + `tika-parsers-standard-package` (PDF via PDFBox, PPTX via POI, language detection)
- `com.microsoft.graph:microsoft-graph` + `com.microsoft.azure:msal4j` (Albania SharePoint, later)
- `jackson-databind` (JSON for store + profiles); `java.util.zip` is built-in
- *(local-embeddings fallback only)* `ai.djl:api` + `ai.djl.huggingface:tokenizers` + a PyTorch engine

**Frontend (`frontend/`):** `ng new frontend` (Angular CLI), `HttpClientModule`, Angular Router. Needs Node.js.

**Config (`application.yml`, all from env vars — no secrets committed):**
- **LLM:** `ANTHROPIC_API_KEY`, `CLAUDE_MODEL=claude-opus-4-8`
- **Embeddings:** `EMBEDDING_PROVIDER=voyage` (`voyage` | `djl`), `VOYAGE_API_KEY`, `EMBEDDING_MODEL=voyage-3`
- **Albania source:** `ALB_SOURCE=sharepoint` (`sharepoint` | `local`), `ALB_ZIP_PATH`, plus `GRAPH_TENANT_ID/CLIENT_ID/CLIENT_SECRET`, `SHAREPOINT_SITE_ID`, `SHAREPOINT_DRIVE_ID`
- **Serbia source (configurable ZIP — location unknown):** `SRB_SOURCE=zip`, `SRB_ZIP_PATH`, `SRB_EXTRACT_DIR`
- **Auth / internal-only (#3):** `AUTH_MODE=mock` (`mock` | `entra`), `MOCK_PROFILE_PATH=./data/mock_profiles/`; (later) `ENTRA_TENANT_ID/CLIENT_ID/CLIENT_SECRET/REDIRECT_URI`
- **Store:** `INDEX_PATH=./data/index.json`
- **CORS:** allow `http://localhost:4200` (Angular dev server) so the frontend isn't blocked
- `.gitignore`: `target/`, `node_modules/`, `data/index.json`, `data/_srb_extracted/`, `.env`, `*.local`

### Step 2 — Document ingestion sources (per office)

Each office has its **own** source. Office is assigned by the source, not by file content, and is
**never** inferred from language. The two offices are indexed independently and tagged
`office=albania` / `office=serbia` at ingestion. (Confirmed from the data: filenames and languages
are unreliable — many Serbia files are labelled "Eng Serbia".)

**SourceService abstraction (`ingestion/SourceService.java`)**
- One interface: `Stream<SourceDoc> iterDocuments(String office)` where `SourceDoc = {bytes/path, name, sourceUrl, lastModified, office, fileType}`
- Backends selected by config: `sharepoint`, `zip`, `localFolder`
- Downstream pipeline (DocumentLoader → Indexer) is identical regardless of backend

**Albania — primary: SharePoint (`SharePointClient.java`)**
- Authenticate via **MSAL4J** client-credentials flow (app registration)
- List drive items under the Albania HR folder via the **Microsoft Graph Java SDK**; keep `.pdf` and `.pptx`
- Download bytes via `GET /drives/{driveId}/items/{itemId}/content` (use `/content`, not a `downloadUrl` field)
- Fallback `ALB_SOURCE=local`: read `ALB_ZIP_PATH` (`Politikat dhe rregulloret e reja të Engineering Albania.zip`, 4 PDFs) — demo stays valid

**Serbia — ZIP at a configurable path (`ZipSource.java`)**
- The Serbia documents live in a ZIP whose location is **not fixed** — read it from `SRB_ZIP_PATH`
  (absolute or relative; do **not** hardcode). Validate the path exists and fail with a clear error.
- Extract into `SRB_EXTRACT_DIR` (`java.util.zip`), walk the tree, tag every file `office=serbia`
- Use the local file path for citations (no SharePoint link for Serbia)
- Confirmed contents: 24 PDFs, mixed SR + EN (rulebooks, office rules, FitPass, Workday FAQ)

**Office isolation at ingestion (#1):** ingest per office; every chunk carries its `office`. Albania
docs contain no Serbian-HR content and vice versa, so isolation is purely a metadata-filter problem
at query time (Step 5) — but the tag must be correct here first.

### Step 3 — Document processing (`DocumentLoader.java`, Apache Tika)

- **Apache Tika** auto-detects type and extracts text for both formats with one API:
  - `.pdf` → PDFBox under the hood; capture page numbers where the parser exposes them
  - `.pptx` → POI (XSLF) under the hood; capture slide number as the `page` field; pull notes/tables text too
  - `.ppt` (legacy) / image-only slides → log + skip (or convert via LibreOffice headless if time allows)
- Store metadata: `sourceName`, `sourceUrl`, `lastModified`, `page` (page **or** slide), `office` (from the **source**, never filename/language), `fileType`, `language`
- Language detection (Tika `LanguageDetector`) → metadata for the **reply** language and citations, **not** for access control (office is the access boundary)
- **Scanned PDFs / image-only slides:** if extracted text is empty/very short, log a warning and flag; OCR (Tesseract via Tika) only if time allows

### Step 4 — Indexing (`Indexer.java` + `store/VectorStore.java`)

- Chunk text: ~800 chars, ~100 overlap (simple sliding-window splitter)
- Embed via `EmbeddingClient` (Voyage REST primary; DJL local fallback) — the **same** model embeds
  documents and queries
- **This is the "context in DB":** embeddings + chunks computed **once** and persisted to
  `data/index.json`, so the LLM never re-reads source files at query time — it only sees retrieved chunks
- `VectorStore` loads the JSON at startup into memory; cosine similarity over `float[]` vectors
- Each chunk's id = `sourceId + ":" + lastModified + ":" + chunkIndex` → **incremental upsert**: a new
  doc embeds only itself; a changed doc (new `lastModified`) re-embeds only that doc
- Persist after each ingestion run; reload on boot (survives restarts)

### Step 5 — Retriever (`RetrieverService.java`) — enforces office isolation (#1)

- `retrieve(String query, String office, int k)` → `office` is **required** (no "all" for end users).
  The caller passes the **authenticated session's** office; it is never taken from the client or the LLM.
- Apply the office filter **as a predicate before/within** the cosine ranking, so other-office chunks
  never enter the LLM context or citations
- Returns `List<RetrievedChunk>` = `{content, sourceName, sourceUrl, lastModified, page, score, office}`
- **Isolation test (must pass):** a Serbia-office query (even written in Albanian) returns zero
  `office=albania` chunks, and vice versa

### Step 6 — Agent tools (`agent/HrTools.java`)

Define two Claude tools (official Anthropic Java SDK tool use):

```
searchHrDocuments(query: string) -> string
# office is NOT a tool argument — it is bound from the authenticated session BEFORE the agent runs,
# so the LLM cannot widen scope. Calls RetrieverService.retrieve(query, sessionOffice, k),
# returns formatted excerpts with source names + dates.

getDocumentMetadata(sourceName: string) -> string
# Returns lastModified + source URL for a document (same office filter applied).
```

> Binding office server-side (not as a model-visible parameter) is deliberate: it removes any path
> for prompt-injection or model error to retrieve the other office's documents. Document in SPEC.md.

### Step 7 — Agent orchestrator (`agent/HrAgentService.java`)

- `answer(String question, String language, String office)` → runs the Claude tool-use loop via the
  Anthropic Java SDK (`client.messages().create(...)` with the two tools and adaptive thinking; loop
  while `stopReason == TOOL_USE`, or use the SDK's beta tool runner). `office` is captured for the
  tool closures, never exposed to the model.
- System prompt instructs:
  - Always reply in `{language}`
  - Cite the source document name and date in every answer
  - Never answer from prior knowledge — only from retrieved documents
  - **Scope (#2):** answer **only** HR questions grounded in retrieved docs. Off-topic (weather, general
    knowledge, coding, …) → refuse: "I can only help with HR questions for the {office} office, based on our HR documents."
  - Never mention or speculate about the other office's documents
  - If no relevant document found, say so clearly (do not improvise)
- **Scope gate (`ScopeGuard.java`, #2):** if top retrieval score is below threshold / no chunk passes →
  return the HR-only refusal **without** calling the LLM. Optional cheap off-topic pre-check (Claude
  Haiku 4.5 or keyword/embedding) to short-circuit obvious non-HR input. Grounding gate is primary.

**Simplification fallback (if behind schedule):**
- Replace the tool-use loop with a single RAG call: `retrieve(office) → Claude with context in the prompt → answer`
- Still document the design in SPEC.md and keep one tool conceptually
- The tool-use agent is preferred for the **10% agent setup** score, but a working RAG call beats a broken agent
- Either way the office filter and scope gate stay enforced

**Agent design decision — single agent, not multi-agent:**
- One Claude agent handles **all** HR topics (leave, benefits, IT, rulebooks, …). We do **not** split by topic or office.
- The real boundaries are enforced *around* the agent: **office** = server-side retrieval filter (#1), **scope** = grounding gate (#2). Neither is an agent split.
- Multiple agents add latency, coordination, and cost for no benefit, and score worse on the agent-spec criterion than one well-specified agent.

### Step 8 — REST API (Spring Boot `web/`) + Angular UI (`frontend/`)

**REST API (Spring Boot) — office lives in the server session, never the request body:**
- `POST /api/login` `{username}` (mock) → creates server session, stores resolved `office`; returns `{upn, displayName, office}`
- `POST /api/chat` `{question}` → backend reads `office` **from the session**, runs the agent, returns
  `ChatResponse {text, language, citations:[{sourceName,lastModified,url,page}], refused, reason}`
- `GET /api/status` → `{office, docCount, lastIndexed}`
- **Spring Security** rejects `/api/chat` and `/api/status` without a valid session → enforces #3 server-side
  (the Angular guard is UX only; the real gate is here)

**Angular UI:**
- **Auth gate first (#3):** `auth.guard.ts` blocks routes until logged in; `login/` posts to `/api/login`
  - `AUTH_MODE=mock`: pick a user whose Graph `/me/profile` JSON is in `data/mock_profiles/`
  - `AUTH_MODE=entra` (later): MSAL Angular SSO returns the same profile shape → same backend resolution
- `chat/`: message history, input → `api.chat()`; renders answer, **citations expander**, **language badge** (EN/SR/SQ), **refused/empty** states
- `sidebar/`: **office badge read-only** (no selector — removes the isolation loophole), `/api/status` display
- `session.service.ts` holds `{user, office, messages, lang}`; **office is display-only**, never sent on `/api/chat`
- Every turn audited server-side (`AuditService`): user, office, question, answered/refused
- **Re-index** is an admin/CLI action (`--ingest=...`), not a UI button — pre-run before the demo

### Step 9 — SPEC.md (agent specification — 20% of score)

Cover:
- Problem statement
- Agent architecture diagram (ASCII)
- Tool schemas with input/output types (JSON Schema style)
- System prompt (full text)
- **Model & embedding choice + rationale:** Claude Opus 4.8 via official **Java** SDK (tool use); **Voyage AI** multilingual embeddings because Anthropic has no embeddings endpoint and Python `sentence-transformers` is unavailable (DJL local noted as offline fallback)
- **Single-agent decision** (why not multi-agent / not topic-split)
- Retrieval strategy and chunking rationale ("context in DB" + incremental upsert; in-memory cosine store)
- Language handling approach
- Document versioning approach
- Fallback strategy (local zip if SharePoint down; RAG call if tool-use loop misbehaves; DJL if no Voyage key)
- **Security & access restrictions** — office isolation, HR-scope, internal-only

---

## Security & Access Restrictions

Three hard requirements, enforced **server-side**. Document each in SPEC.md with *how* it is enforced —
judges reward this directly (production-readiness, 40%).

### #1 — Office isolation (Serbia ⇎ Albania)

- A user from one office must **never** receive a document from the other office — **even when the
  question is written in the other office's language** (a Serbia user asking in Albanian still gets only Serbia docs).
- Enforcement chain (defense in depth):
  1. **Ingestion:** every chunk tagged `office` from its **source** (never filename/language).
  2. **Identity:** `office` derived from the authenticated user and stored in the **server session**.
  3. **Retrieval:** mandatory office filter inside the vector-store query.
  4. **Agent:** `office` bound server-side for the tools — not a model-visible argument (no prompt-injection path).
  5. **API/UI:** the Angular client never sends `office`; there is no office selector.
- Language detection is decoupled from office: it only controls the **reply** language.
- **Test:** Serbia-office query in Albanian → 0 Albanian sources; and the reverse.

### #2 — HR scope only (no off-topic answers)

- The bot answers **only** HR questions grounded in the indexed documents. "What's the weather?",
  general knowledge, coding help, etc. are refused.
- Enforcement:
  1. **Grounding gate (primary):** no retrieved chunk above threshold → HR-only refusal, no LLM guess.
  2. **System prompt:** explicit scope + refusal + "never use prior knowledge."
  3. **Optional classifier:** cheap off-topic pre-check (Haiku 4.5 / keyword) to short-circuit obvious non-HR input.

### #3 — Internal employees only

- No anonymous access. **Spring Security** gates every `/api/*` data endpoint server-side; the Angular
  `auth.guard` is UX only.
- **Production:** Entra ID / Azure AD SSO (MSAL Angular + backend validation) — also yields the user's office for #1.
- **Hackathon dev (`AUTH_MODE=mock`):** `POST /api/login` reads a **Microsoft Graph `/me/profile` JSON**
  per user (a real sample is captured in `data/mock_profiles/`). Same shape SSO returns → drop-in swap later.

**Identity → office derivation (`auth/IdentityService.java`)**

From the real Graph profile we have, the load-bearing fields are:

```
account[0].userPersona         = "internalMember"     → gate: must be internalMember (#3)
account[0].userPrincipalName   = "Urim.Toshi@eng.it"  → identity / audit
positions[0].detail.company.displayName = "Engineering Albania SHPK"
positions[0].detail.company.department  = "Sede di Tirana"
positions[0].detail.company.address.city = "Tirana"
```

- **Internal gate:** require `userPersona == "internalMember"` (and a valid org email domain). Reject otherwise.
- **Office resolution** (first match wins, case-insensitive), from the **current** position (`isCurrent == true`):
  - `company.displayName` contains `"Albania"` → `office = albania`
  - `company.displayName` contains `"Serbia"` / `"Srbija"` → `office = serbia`
  - else fall back to `address.city` / country (`Tirana` → albania, `Beograd`/`Belgrade` → serbia)
  - if still unresolved → **deny access** (fail closed; never default to an office).
- The resolved `office` is stored in the **server session** and injected into every retrieval. The user
  never sees or chooses it. Same code path for the mock JSON and the live Graph response.
- Audit log of every interaction (user, office, question, answered/refused) as a supporting control.

---

## Build Priority (if time is tight)

| Priority | Task | Why |
|----------|------|-----|
| 1 | Maven + Angular skeletons, `application.yml`, CORS, Serbia zip + Albania local zip in `data/` | Unblocks everyone; real data both offices |
| 2 | Tika loader (pdf+pptx) + Indexer tagging `office` + in-memory store (JSON) | Core retrieval + isolation foundation (#1) |
| 3 | RetrieverService with mandatory office filter + ScopeGuard | Restrictions #1 and #2 |
| 4 | Spring Security session gate + `/api/login` `/api/chat` + Angular shell/login/chat | 40% production-ready + #3 + live demo |
| 5 | HrAgentService (Claude tool use) + `SPEC.md` | 20% spec + 10% agent setup |
| 6 | SharePoint Graph (Albania) + Entra SSO | Swap in when credentials work |

**Rule:** if SharePoint/SSO is not working by ~14:00, use the Albania local zip + `AUTH_MODE=mock` and
do not block the demo. The three restrictions stay enforced in both paths.

---

## Key Things to Keep in Mind

### SharePoint "always latest version"
- Store `lastModifiedDateTime` as chunk metadata; use `sourceId + lastModified` in the chunk id
- Run ingestion (`--ingest=all`) once before the demo to ensure the index is fresh
- Optionally: Graph delta query to detect changed files (only if time allows)

### Multilingual retrieval
- **Voyage** multilingual embeddings handle EN/SR/SQ well; the **same** model must embed documents and queries
- For best cross-lingual recall: optionally translate query → English before embedding, then answer back — adds latency; skip unless quality is poor in testing
- Always pass `language` to the agent so **Claude** replies in that language
- If language detection misclassifies short text, allow a manual language override in the UI (optional)
- *DJL fallback:* a multilingual sentence-transformers model (e.g. `paraphrase-multilingual-MiniLM-L12-v2`); E5 variants need `query:`/`passage:` prefixes

### Agent spec quality (20% of score)
- SPEC.md is the second-highest scoring criterion — formal tool schemas (JSON Schema style), exact system prompt, and *why* each choice was made

### Production-readiness (40% — highest weight)
- Graceful handling: no docs found, API errors, empty PDFs, no Voyage key (→ DJL)
- Config via env / `application.yml` (no hardcoded secrets); structured logging with timestamps
- Pinned deps in `pom.xml` / `package.json`; README with setup + demo steps; index persisted to `data/index.json`

### Demo preparation
- Pre-run ingestion before the demo — do **not** rely on live re-index during the presentation
- 3 demo questions (EN, SR, SQ): leave policy, benefits, plus a no-answer edge case
- Highlight the source citation and the office badge on screen
- Run backend + `ng serve` and test all three languages + the isolation probe the night before

---

## How to Run

```bash
# Prereqs: JDK 17+, Maven, Node.js + Angular CLI

# 1. Backend config — set env vars (no secrets committed)
#    ANTHROPIC_API_KEY, VOYAGE_API_KEY (or EMBEDDING_PROVIDER=djl), AUTH_MODE=mock
#    SRB_ZIP_PATH=<wherever the Serbia zip lives>, ALB_SOURCE=local + ALB_ZIP_PATH (or SharePoint creds)

# 2. Index documents per office (run before demo) — Spring Boot one-off
cd backend
mvn -q spring-boot:run -Dspring-boot.run.arguments=--ingest=all
# or: --ingest=serbia  /  --ingest=albania

# 3. Start the backend API (http://localhost:8080)
mvn spring-boot:run

# 4. Start the Angular frontend (http://localhost:4200)
cd ../frontend
npm install
ng serve
```

Open `http://localhost:4200`, sign in (mock profile), and chat.

---

## Verification / Testing

1. Ingestion: `mvn spring-boot:run -Dspring-boot.run.arguments=--ingest=all` → logs document + chunk count per office
2. Retriever (unit test): `retrieve("annual leave", "serbia", 5)` returns Serbia chunks only
3. Agent (unit/integration test): `answer("How many vacation days do I get?", "en", "serbia")` → grounded, cited
4. Full app: backend + `ng serve` → chat in EN, SR, SQ; verify language match + citations
5. Edge case: question with no relevant docs → graceful "not found" response
6. Restart backend → confirm `data/index.json` loads without re-ingesting
7. **Office isolation (#1):** as a Serbia user, ask (in Albanian) about an Albania-only policy → 0 Albanian sources + "not found in your office's documents"; repeat reversed
8. **HR scope (#2):** ask "what's the weather today?" → HR-only refusal, no LLM guess
9. **Internal-only (#3):** call `POST /api/chat` without a session (curl) → 401/403; open the SPA unauthenticated → blocked by guard
10. **PPTX:** confirm at least one `.pptx`-sourced chunk is retrievable (slide number in citation)

---

## Risk Register

| Risk | Mitigation |
|------|------------|
| Node.js / Angular CLI not installed | Confirm in first 15 min (same gotcha that moved us off Python); install Node LTS |
| No `VOYAGE_API_KEY` for embeddings | Switch `EMBEDDING_PROVIDER=djl` (local, no key, heavier); confirm early |
| No `ANTHROPIC_API_KEY` / rate limits | Confirm key first; Java SDK auto-retries 429/5xx; Haiku 4.5 for the optional classifier |
| SharePoint Graph auth/permissions delay (Albania) | Local zip fallback via `ALB_SOURCE=local` + `ALB_ZIP_PATH` |
| Serbia zip not at expected path | Configurable `SRB_ZIP_PATH`; validate + clear error on missing |
| Scanned/image-only PDFs or slides | Tika returns little text → log + flag; OCR only if time allows |
| `.pptx` text extraction empty | Tika/POI per-slide; log + flag empties; `.ppt` legacy skipped/converted |
| Claude tool-use loop too slow or buggy | Fall back to single RAG call (retrieve→prompt→answer); document in SPEC.md |
| DJL native (PyTorch) download slow/large | Pre-download before demo; prefer Voyage if a key is available |
| CORS blocks Angular → API | Allow `http://localhost:4200` in `CorsConfig` from the start |
| Entra SSO not ready by demo (#3) | Use `AUTH_MODE=mock` with Graph-profile JSON; same `IdentityService` path, office stays server-side |
| Office mis-tag leaks cross-border docs (#1) | Tag from source not filename; mandatory query filter; isolation test must pass |
| Client tampering to widen scope (#1/#2) | `office` comes from server session, never the request; tools bound server-side; grounding gate |
| Late commits not counted | Push to GitHub before 22:00; add reviewers early |
```
