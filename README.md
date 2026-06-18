# Kernel HR Assistant

Multilingual HR chatbot (EN / SR / SQ) grounded in HR documents from SharePoint (Albania) and a configurable ZIP (Serbia). Built with Spring Boot + React/Vite + Claude Opus 4.8 + Voyage AI embeddings + PostgreSQL/pgvector.

**Documentation:**
- [ARCHITECTURE.md](./ARCHITECTURE.md) — Full technical architecture, data flows, security model, API contract
- [SPEC.md](./SPEC.md) — Agent specification: tool schemas, system prompt, retrieval strategy, security layers
- [LLM_UTILIZATION.md](./LLM_UTILIZATION.md) — LLM usage details: models, prompting, tool-use protocol, rate limits

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Node.js | 18+ | `node -version` |
| Docker | any | for the database |

---

## 1. Start the database

The database runs in Docker using the official `pgvector/pgvector:pg17` image, which ships with the pgvector extension pre-installed.

```bash
docker compose up -d
```

This starts PostgreSQL 17 on port **5433** with:
- User: `postgres` / Password: `admin`
- Database: `hrdb` (created automatically)
- Named volume `hr_pgdata` for data persistence

On first application startup, Spring Boot runs `schema.sql` which enables the `vector` extension and creates the `chunks` table and indexes.

**Stop the database:**
```bash
docker compose down
```

**Stop and wipe all data (reset):**
```bash
docker compose down -v
```

---

## 2. Configure environment variables

Copy and edit the values for your environment. No secrets should be committed to git.

```bash
# ── LLM ──────────────────────────────────────────────────────────────────────
export ANTHROPIC_API_KEY=sk-ant-...
export CLAUDE_MODEL=claude-opus-4-8          # default

# ── Embeddings ───────────────────────────────────────────────────────────────
export EMBEDDING_PROVIDER=voyage             # voyage | djl
export VOYAGE_API_KEY=pa-...
export EMBEDDING_MODEL=voyage-3              # default

# ── Database ─────────────────────────────────────────────────────────────────
export DATABASE_URL=jdbc:postgresql://localhost:5433/hrdb
export DATABASE_USER=postgres
export DATABASE_PASSWORD=admin

# ── Serbia documents (ZIP) ───────────────────────────────────────────────────
export SRB_ZIP_PATH=/path/to/serbia_hr_docs.zip

# ── Albania documents ────────────────────────────────────────────────────────
# Option A — SharePoint (production)
export ALB_SOURCE=sharepoint
export GRAPH_TENANT_ID=...
export GRAPH_CLIENT_ID=...
export GRAPH_CLIENT_SECRET=...
export SHAREPOINT_DRIVE_ID=...

# Option B — local ZIP fallback (if SharePoint credentials are not available)
export ALB_SOURCE=local
export ALB_ZIP_PATH=/path/to/albania_hr_docs.zip

# ── Auth ─────────────────────────────────────────────────────────────────────
export AUTH_MODE=mock                        # mock | entra
export MOCK_PROFILE_PATH=./data/mock_profiles/
```

---

## 3. Index documents

Run once before the demo to populate the vector store. The index is persisted in PostgreSQL and survives restarts — no need to re-index every time.

```bash
cd backend

# Index Serbia documents only
mvn spring-boot:run -Dspring-boot.run.arguments=--ingest=serbia

# Index Albania documents only
mvn spring-boot:run -Dspring-boot.run.arguments=--ingest=albania

# Index both offices (recommended before demo)
mvn spring-boot:run -Dspring-boot.run.arguments=--ingest=all
```

What happens during ingestion:
1. Documents are extracted from the ZIP / downloaded from SharePoint
2. Apache Tika parses PDF and PPTX files into text
3. Text is split into ~800-character chunks with 100-character overlap
4. Each chunk is embedded via Voyage AI (`voyage-3`, 1024 dimensions)
5. Chunks are upserted into PostgreSQL with their `office` tag and embedding vector

---

## 4. Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

On startup the application will:
- Connect to PostgreSQL (create the `hrdb` database if it does not exist)
- Run `schema.sql` (enables pgvector, creates `chunks` table and indexes if missing)
- Load the existing index from PostgreSQL into memory for fast query serving

---

## 5. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

---

## 6. Demo flow

1. Sign in using a mock profile (Serbia or Albania user)
2. Ask an HR question in English, Serbian, or Albanian
3. The answer is grounded in the indexed documents for that office only
4. Citations show the source document name, date, and page/slide number

**Demo questions to prepare:**
- `"How many vacation days am I entitled to?"` (EN)
- `"Koliko dana godišnjeg odmora imam pravo?"` (SR)
- `"Sa ditë pushimi kam të drejtë?"` (SQ)
- `"What is the weather today?"` → should be refused (out of scope)

---

## API endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/login` | `{"username": "Urim.Toshi"}` → creates session |
| `POST` | `/api/chat` | `{"question": "..."}` → grounded answer + citations |
| `GET` | `/api/status` | Office, document count, last indexed |

`/api/chat` and `/api/status` require an active session — unauthenticated requests return `401`.

---

## Database

The vector store uses PostgreSQL 17 with the [pgvector](https://github.com/pgvector/pgvector) extension.

```
chunks table
┌──────────────┬──────────────┬──────────────────────────────────────────┐
│ id           │ VARCHAR(512) │ PK — "filename:lastModified:chunkIndex"  │
│ content      │ TEXT         │ Raw chunk text (~800 chars)              │
│ office       │ VARCHAR(50)  │ "serbia" | "albania" — mandatory filter  │
│ source_name  │ VARCHAR(512) │ Original filename                        │
│ source_url   │ TEXT         │ Local path or SharePoint URL             │
│ last_modified│ VARCHAR(64)  │ ISO-8601 — drives idempotent re-index    │
│ page         │ INTEGER      │ Page (PDF) or slide (PPTX), nullable     │
│ file_type    │ VARCHAR(10)  │ "pdf" | "pptx"                           │
│ language     │ VARCHAR(10)  │ "en" | "sr" | "sq"                       │
│ vector       │ vector(1024) │ Voyage-3 embedding — used for search     │
└──────────────┴──────────────┴──────────────────────────────────────────┘
```

Similarity search uses pgvector's `<=>` cosine distance operator with an IVFFlat index. The `office` filter is always applied before the vector scan — documents from the other office never enter the candidate set.

---

## Project structure

```
kernel-hackathon/
├── docker-compose.yml          # PostgreSQL 17 + pgvector
├── backend/                    # Spring Boot (Maven)
│   └── src/main/java/com/kernel/hr/
│       ├── ingestion/          # Document loading, parsing, chunking, embedding
│       ├── store/              # VectorStore (PostgreSQL/pgvector backed)
│       ├── embedding/          # VoyageEmbeddingClient
│       ├── retrieval/          # RetrieverService (office-filtered search)
│       ├── agent/              # Claude tool-use agent (HrAgentService)
│       ├── auth/               # IdentityService, AuthController
│       └── web/                # REST controllers + DTOs
├── frontend/                   # Angular SPA
│   └── src/app/
│       ├── components/         # chat, citation, login, sidebar
│       ├── services/           # ApiService, MockApiService, SessionService
│       └── guards/             # AuthGuard
└── data/
    └── mock_profiles/          # Graph /me/profile JSON for dev login
```
