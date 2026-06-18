# Kernel HR Assistant — Agent Specification

> **Status:** Work in progress. Sections marked [TODO] to be completed by deadline.

---

## 1. Problem Statement

Engineering Albania (Tirana) and Engineering Serbia (Belgrade) employees need instant, accurate answers to HR questions in their native languages (Albanian SQ, Serbian SR, English EN). HR documents are scattered across SharePoint (Albania) and a ZIP archive (Serbia). This assistant provides a grounded, multilingual chatbot that answers only from official HR documents, enforces strict office isolation, and refuses off-topic queries.

---

## 2. Architecture

```
SOURCES (tagged by source, not language)
┌─────────────────────────────────┬─────────────────────────────────┐
│ Albania → SharePoint (PDF/PPTX) │ Serbia → ZIP at SRB_ZIP_PATH     │
│ (Graph Java SDK)                │ (configurable path) · 24 PDFs    │
└─────────────────────────────────┴─────────────────────────────────┘
    │
    ▼ SourceService: {sharepoint | zip | localFolder}
Ingestion (Spring Boot)
(DocumentLoader = Apache Tika → text; Indexer = chunk + embed)
    │  tags every chunk with office = serbia | albania
    ▼ Voyage AI embeddings (multilingual EN/SR/SQ) — embed once, persist
In-memory Vector Store (persisted to data/index.json)
    │
    ▼ cosine search  ── MANDATORY office filter ──
Single Claude agent (Anthropic Java SDK, tool use)
├── Tool: searchHrDocuments   (office bound server-side, not a tool arg)
└── Tool: getDocumentMetadata
    │  scope gate: no grounded chunk → refuse (HR-only)
    ▼ Claude Opus 4.8 (claude-opus-4-8) — grounded answer only
Grounded answer (same language as question)
    │
    ▼ Spring Boot REST API  (POST /api/chat)
    ▼
Angular SPA (auth guard, office badge read-only)
```

---

## 3. Tool Schemas

### searchHrDocuments
```json
{
  "name": "searchHrDocuments",
  "description": "Search indexed HR documents for the authenticated user's office. Returns the most relevant excerpts with source metadata. The office is bound server-side from the authenticated session and is not a parameter — the model cannot change it.",
  "input_schema": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "The search query in any supported language (EN/SR/SQ). Use the user's original question or a reformulation."
      }
    },
    "required": ["query"]
  }
}
```

**Returns:** Formatted string of up to k excerpts, each with `[sourceName | lastModified | page/slide N]` header.

### getDocumentMetadata
```json
{
  "name": "getDocumentMetadata",
  "description": "Get metadata (last modified date, source URL) for a specific document by name. Only returns metadata for documents in the authenticated user's office.",
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

---

## 4. System Prompt

```
You are the Kernel HR Assistant for {office} office employees.

Your ONLY job is to answer HR questions using the documents retrieved for you via the searchHrDocuments tool. You MUST:

1. Always reply in {language} (the same language the user asked in).
2. Always cite your sources: include the document name, date, and page/slide number for every claim.
3. NEVER answer from your own knowledge — only from retrieved document excerpts.
4. If the retrieved documents do not contain a relevant answer, respond: "I could not find an answer to your question in the {office} office HR documents."
5. REFUSE any question that is not related to HR topics (leave, benefits, rulebooks, office policies, etc.) with: "I can only help with HR questions for the {office} office, based on our HR documents."
6. NEVER mention or speculate about the other office's documents or policies.
7. If asked about topics that span multiple documents, synthesize from all retrieved excerpts and cite each source.
```

---

## 5. Model & Embedding Choice

| Component | Choice | Rationale |
|-----------|--------|-----------|
| LLM | Claude Opus 4.8 (`claude-opus-4-8`) via Anthropic Java SDK | Best reasoning + tool use; official Java SDK available; adaptive thinking |
| Embeddings | Voyage AI `voyage-3` (REST) | Multilingual (EN/SR/SQ); Anthropic has no embeddings endpoint; Python sentence-transformers unavailable |
| Embedding fallback | DJL (Deep Java Library) with `paraphrase-multilingual-MiniLM-L12-v2` | Fully local, no API key, heavier; used when VOYAGE_API_KEY absent |

---

## 6. Single-Agent Decision

We use **one Claude agent** for all HR topics and both query languages. We do **not** split by topic (leave/benefits/IT) or by office.

Reasons:
- The real isolation boundary is server-side (mandatory office filter in retrieval), not an agent split.
- Multiple agents add latency, coordination overhead, and cost with no accuracy gain.
- One well-specified agent with tight tool definitions scores better on the agent specification criterion.
- Topic routing would require a classifier, adding latency and a failure mode; the vector search already surfaces topic-relevant chunks.

---

## 7. Retrieval Strategy

- **Chunking:** ~800 chars, ~100 overlap (sliding window).
- **Embedding:** Same Voyage `voyage-3` model for both documents and queries (critical for cosine similarity to work cross-lingually).
- **Office filter:** Applied as a predicate *before* cosine ranking — other-office chunks never enter the scoring pool.
- **k:** Top 5 chunks per tool call.
- **Incremental upsert:** Chunk ID = `sourceId + ":" + lastModified + ":" + chunkIndex`. Only changed documents (new `lastModified`) are re-embedded on re-index.
- **Persistence:** `data/index.json` — loaded into memory on startup; avoids re-ingesting on restart.

---

## 8. Language Handling

- Language detected via Apache Tika `LanguageDetector` on the user's question.
- Detected language passed to the agent in the system prompt (`{language}`).
- Claude replies in the detected language.
- Language detection controls **only** the reply language — it has no effect on office assignment or document access.

---

## 9. Security & Access Restrictions

### #1 — Office Isolation (Serbia ⇎ Albania)
[TODO — Agathi: describe enforcement chain]

### #2 — HR Scope Only
[TODO — Agathi: describe grounding gate + system prompt enforcement]

### #3 — Internal Employees Only
[TODO — Agathi: describe Spring Security session gate + Entra/mock auth]

---

## 10. Fallback Strategy

| Scenario | Fallback |
|----------|----------|
| No `VOYAGE_API_KEY` | Switch to `EMBEDDING_PROVIDER=djl` (local HuggingFace model) |
| SharePoint Graph auth fails (Albania) | Set `ALB_SOURCE=local` + `ALB_ZIP_PATH` |
| Claude tool-use loop fails | Fall back to single RAG call (retrieve → context in prompt → answer) |
| Scanned/image-only PDF | Log + skip (Tika returns empty text); OCR via Tesseract optional |

---

## 11. Document Versioning

- `lastModifiedDateTime` stored as chunk metadata.
- Chunk ID includes `lastModified` → changed document triggers re-embed of only that document.
- Re-index via `--ingest=all` before demo.
