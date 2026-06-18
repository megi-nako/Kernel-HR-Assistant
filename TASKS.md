# Kernel HR Assistant — Task Breakdown & Work Split (Java / Angular)

Companion to `Kernel-HR-Assistant-Plan.md`. Four engineers in parallel without blocking each other.
**Deadline: 22:00 today.** Stack: **Spring Boot (Java)** backend + **Angular** frontend.

- **Backend:** Megi (write side — docs → vector store), Agathi (read side — query → grounded answer + REST/security)
- **Frontend:** Loerti (chat experience), Urim (app shell + auth + sidebar)

> **Golden rule:** contracts first, stubs immediately. Nobody waits on a real implementation —
> everyone codes against a stub and swaps it at a checkpoint.

> **Confirm in first 15 min:** `ANTHROPIC_API_KEY` works · `VOYAGE_API_KEY` available (else
> `EMBEDDING_PROVIDER=djl`) · **Node.js + Angular CLI installed** · JDK 17+ and Maven installed.

---

## Phase 0 — Contracts & skeleton (≈45 min, ALL FOUR TOGETHER)

Create the two skeletons + the 5 contract seams **with signatures, DTOs, and a working mock**, commit
to `main`, then branch. The frontend↔backend wall is the **REST API DTOs + an Angular MockApiService**.

### Shared setup (split between whoever's free)
- [ ] `git init`, push, add reviewers `vstanojevic` and `marest94`
- [ ] `backend/` Spring Boot via start.spring.io (web, security); `frontend/` via `ng new frontend`
- [ ] `backend/pom.xml` deps: `spring-boot-starter-web`, `-security`, `com.anthropic:anthropic-java`, `tika-core` + `tika-parsers-standard-package`, `microsoft-graph` + `msal4j`, `jackson`
- [ ] `application.yml` reads env vars (ANTHROPIC_API_KEY, CLAUDE_MODEL, EMBEDDING_PROVIDER/VOYAGE_API_KEY/EMBEDDING_MODEL, SRB_ZIP_PATH, ALB_*, AUTH_MODE=mock, MOCK_PROFILE_PATH, INDEX_PATH)
- [ ] `CorsConfig`: allow `http://localhost:4200` (so Angular dev server isn't blocked)
- [ ] `.gitignore`: `target/`, `node_modules/`, `data/index.json`, `data/_srb_extracted/`, `.env`, `*.local`

### The 5 contracts (commit signatures + a mock)

**A — Vector store + embedding (owner: Megi)** — `store/Chunk.java`, `store/VectorStore.java`, `embedding/EmbeddingClient.java`
```java
record Chunk(String id, String content, String office, String sourceName, String sourceUrl,
             String lastModified, Integer page, String fileType, String language, float[] vector) {}
interface EmbeddingClient { float[] embed(String text); List<float[]> embedAll(List<String> texts); }
class VectorStore {                       // in-memory, persisted to INDEX_PATH (JSON)
    void upsert(List<Chunk> chunks);      // id = sourceId+":"+lastModified+":"+chunkIdx
    List<Scored> search(float[] q, String office, int k);  // MANDATORY office predicate
    void save(); void load();
}
```

**B — RetrieverService (owner: Agathi)** — `retrieval/RetrieverService.java`
```java
record RetrievedChunk(String content, String sourceName, String sourceUrl,
                      String lastModified, Integer page, double score, String office) {}
List<RetrievedChunk> retrieve(String query, String office, int k);  // office REQUIRED
```

**C — IdentityService (owner: Agathi)** — `auth/IdentityService.java`
```java
record User(String upn, String displayName, String office, boolean internal) {}
String resolveOffice(JsonNode profile);   // "serbia"|"albania"|null (fail-closed)
User   loadUser(JsonNode profile);         // throws if not internalMember
List<String> listMockUsers();              // dev login dropdown
```

**D — REST API DTOs + Angular MockApiService (owner: Agathi defines DTOs; FE builds on the mock)** — the frontend↔backend wall
```
POST /api/login {username}            -> LoginResponse {upn, displayName, office}
POST /api/chat  {question}            -> ChatResponse {text, language,
                                          citations:[{sourceName,lastModified,url,page}], refused, reason}
GET  /api/status                      -> StatusResponse {office, docCount, lastIndexed}
# office is ALWAYS read from the server session — NEVER a request field.
```
```ts
// frontend/src/app/services/mock-api.service.ts — same method shapes, canned data.
// Loerti + Urim build the entire UI against this until the backend is live.
```

**E — Angular SessionService shape (owner: Urim)** — `services/session.service.ts`
```ts
{ user: User|null, office: string|null, messages: ChatMsg[], lang: string }
// office is DISPLAY-ONLY; never sent on /api/chat.
```

**P0 exit:** `mvn spring-boot:run` serves a stub `/api/status`; `ng serve` shows a placeholder hitting
`MockApiService`; all 5 contracts compile. Everyone branches.

---

## Megi — Backend, WRITE side (docs → vector store)

Branch: `feat/ingestion-megi`. Owns everything that gets documents into the store.

### P1 — Serbia vertical (real data indexed)
- [ ] `embedding/EmbeddingClient`: Voyage REST impl (`VOYAGE_API_KEY`, `voyage-3`); confirm dimension; **handle no-key → log + DJL toggle**
- [ ] `ingestion/ZipSource`: read configurable `SRB_ZIP_PATH`, validate (clear error if missing), extract to `SRB_EXTRACT_DIR` (`java.util.zip`), tag `office=serbia`
- [ ] `ingestion/DocumentLoader` (Apache Tika): PDF → text (+ page); detect language; flag empty/scanned
- [ ] `store/VectorStore`: in-memory cosine + JSON persist (`INDEX_PATH`); office predicate; load on boot
- [ ] `ingestion/Indexer`: chunk (~800/100) → embed → upsert with full metadata; id = `sourceId+lastModified+chunkIdx` (idempotent)
- [ ] `ingestion/IngestionRunner` (`ApplicationRunner`): `--ingest=serbia` indexes the Serbia zip end-to-end
- [ ] **Verify:** store has Serbia chunks; re-run does not duplicate

### P2 — PPTX, Albania, full pipeline
- [ ] DocumentLoader: `.pptx` via Tika/POI (slide number as `page`, notes/tables); `.ppt`/image-only → log + skip
- [ ] `ingestion/SourceService`: abstraction `iterDocuments(office)` over `zip | localFolder | sharepoint`
- [ ] `ingestion/SharePointClient`: MSAL4J client-credentials + Graph Java SDK; list + `/content` download (Albania, PDF/PPTX); tag `office=albania`
- [ ] Albania local fallback: `ALB_SOURCE=local` reads `ALB_ZIP_PATH`
- [ ] `--ingest=all` (and `=albania`)
- [ ] *(if no Voyage key)* DJL `EmbeddingClient`: HuggingFace multilingual model in JVM

### P3 — Harden / tests
- [ ] Test: pptx extraction non-empty (slide retrievable)
- [ ] Test: incremental re-index (changed `lastModified` re-embeds only that doc)
- [ ] Test: every chunk's `office` correct (from source, never filename/language)
- [ ] **Pre-run `--ingest=all` before the demo**

**Hand-off to Agathi (Checkpoint 1):** populated `data/index.json` matching Contract A.

---

## Agathi — Backend, READ side (query → answer) + REST/security seams

Branch: `feat/agent-agathi`. Owns retrieval, agent, identity, REST controllers, security.

### P1 — Retriever + identity + audit (not blocked by Megi)
- [ ] **Seed util** (test fixture): insert 3 chunks (2 serbia, 1 albania) so retriever is testable without ingestion
- [ ] `retrieval/RetrieverService`: cosine search via VectorStore with **mandatory** office filter; Contract B shape
- [ ] `auth/IdentityService`: internal gate (`userPersona=="internalMember"` + org email); `resolveOffice` from `positions[].detail.company.displayName` ("Albania"/"Serbia") + city fallback (Tirana→albania, Beograd→serbia); **fail-closed**
- [ ] `util/LanguageDetector` (Tika): reply language
- [ ] `audit/AuditService`: append-only log (ts, user, office, question, answered/refused)

### P2 — Agent + scope gate + REST + security
- [ ] `agent/HrTools`: `searchHrDocuments(query)` — office bound from session, **not** a model arg; `getDocumentMetadata`
- [ ] `agent/ScopeGuard`: grounding gate — no chunk above threshold → HR-only refusal (no LLM guess); optional Haiku-4.5 off-topic pre-check
- [ ] `agent/HrAgentService`: Claude tool-use loop (Anthropic **Java** SDK, `claude-opus-4-8`, adaptive thinking); system prompt (reply in `{language}`, cite, only retrieved docs, refuse off-topic/other-office)
- [ ] `web/ChatController` `POST /api/chat`: office **from session**; detect lang → agent → ChatResponse
- [ ] `web/AuthController` `POST /api/login` (mock profiles); `web/StatusController` `GET /api/status`
- [ ] `config/SecurityConfig`: session gate — `/api/chat` + `/api/status` require auth → enforces #3 server-side

### P3 — Tests + SPEC lead
- [ ] Test #1 isolation: Serbia-office query in Albanian → **0 albania sources**; reverse
- [ ] Test #2 scope: "weather" → HR-only refusal, no LLM guess
- [ ] Test #3 internal gate + session gate: non-internal profile denied; `/api/chat` without session → 401/403
- [ ] **SPEC.md** — assemble; own security + agent + single-agent rationale

**Hand-offs:** Contract B real (→ agent at CP1); REST API real (→ frontend at CP2).

---

## Loerti — Frontend, CHAT experience

Branch: `feat/chat-loerti`. Owns the chat + citation components. Builds on `MockApiService`; flips to
real `ApiService` at Checkpoint 2.

### P1 — Full chat UI on the mock
- [ ] `components/chat`: message history from `SessionService.messages`; input box → `api.chat(question)`
- [ ] `components/citation`: **citations expander** per answer — name, date, link, page/slide
- [ ] **Language badge** (EN/SR/SQ) from `response.language`
- [ ] **Refused / empty state**: render `response.reason` when `refused`
- [ ] Never sends `office` (it's server-side); reads display office from session

### P2 — Polish
- [ ] Loading / "thinking…" indicator; optional streaming (SSE) if backend supports it
- [ ] Error states (HTTP error → friendly message); long-answer formatting; citation de-dup
- [ ] **Switch `MockApiService` → real `ApiService`** (Checkpoint 2)

### P3 — Demo
- [ ] 3 demo questions (EN, SR, SQ) incl. one no-answer edge case
- [ ] SPEC.md section: language handling & chat UX
- [ ] Verify language match + citations across all three

---

## Urim — Frontend, SHELL + AUTH + SIDEBAR

Branch: `feat/shell-urim`. Owns app shell, login, auth guard, sidebar, mock data.

### P1 — Shell + auth gate + sidebar on the mock
- [ ] `app.component` / `app.routes`; `services/session.service.ts` (Contract E)
- [ ] `guards/auth.guard.ts`: redirect to login until authenticated (#3, UX side)
- [ ] `components/login`: mock-profile picker → `api.login(username)`; stores session
- [ ] `data/mock_profiles/`: real `Urim.Toshi` Graph profile JSON (→ albania) **+** synthesized Serbia profile (→ serbia)
- [ ] `components/sidebar`: **office badge read-only (NO selector)**, `api.status()` display
- [ ] `services/api.service.ts` (HttpClient) + `services/mock-api.service.ts` (canned)

### P2 — Integration + styling
- [ ] Wire real `/api/login` profiles (Checkpoint 2); logout
- [ ] App styling/theme, header; README (JDK/Maven/Node prereqs, run steps)

### P3 — Demo
- [ ] Demo run-sheet (which user → which office → which questions)
- [ ] SPEC.md section: auth & identity → office model
- [ ] Dry-run sign-in for both offices

---

## Timeline & checkpoints

| Phase | Megi | Agathi | Loerti | Urim |
|-------|------|--------|--------|------|
| **P0** contracts (all together) | store + embedding + Chunk | retriever/identity stubs + **REST DTOs** | reads MockApiService | SessionService + shell skeleton + MockApiService |
| **P1** vertical | Serbia → real index | retriever + identity + audit (seed) | chat UI on mock | login + guard + sidebar + mock profiles |
| **🔗 CP1** | **index ↔ retriever connect (backend real)** | | (mock) | (mock) |
| **P2** complete | pptx + sources + SharePoint + Albania + `--ingest` | agent + scope gate + REST + security + tests | streaming/states + **flip to real ApiService** | real login + styling + README |
| **🔗 CP2** | | **frontend flips MockApiService → ApiService** | end-to-end | end-to-end |
| **P3** harden | clean re-index | SPEC.md lead | demo Qs | demo script |
| **P4** freeze | **pre-run `--ingest=all`** | push + reviewers | dry-run 3 langs | buffer |

---

## Dependency → stub matrix (why nobody is blocked)

| Needs | From | Until real, use |
|-------|------|-----------------|
| Frontend → backend answers | Agathi (REST API) | **`MockApiService`** (P0) → real `ApiService` at CP2 |
| Agathi → Megi's index | Megi (VectorStore) | **3-chunk seed fixture** → real at CP1 |
| Urim → Agathi's identity | Agathi (`IdentityService`) | mock profile JSON + identity stub → real at CP2 |
| Loerti → Urim's session | Urim (SessionService) | shape fixed at P0; temp local display office until shell lands |
| Angular dev server → API | CORS | allow `localhost:4200` from P0 (no late surprise) |

---

## Git workflow

- Branch per person: `feat/ingestion-megi`, `feat/agent-agathi`, `feat/chat-loerti`, `feat/shell-urim`
- Small, frequent PRs into `main`. Disjoint file ownership (backend packages vs Angular components) → near-zero conflicts.
- Shared seams (`VectorStore`, `EmbeddingClient`, REST DTOs, `SessionService` shape) **frozen at P0** — change only by group agreement.
- Gitignored: `target/`, `node_modules/`, `data/index.json`, `.env`
- **All commits before 22:00** — commits after do not count.

---

## Definition of done (demo-ready)

- [ ] Sign in as Serbia user → ask in SR and SQ → answers only from Serbia docs, cited, correct language
- [ ] Sign in as Albania user → same, only Albania docs
- [ ] Cross-office probe (Serbia user asks in Albanian about an Albania-only policy) → 0 other-office sources
- [ ] Off-topic ("weather") → HR-only refusal
- [ ] `POST /api/chat` without a session (curl) → 401/403; SPA unauthenticated → blocked by guard
- [ ] At least one `.pptx`-sourced answer with slide citation
- [ ] `data/index.json` pre-built; backend restarts without re-ingesting
- [ ] SPEC.md + README complete; pushed; reviewers added
```
