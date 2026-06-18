---
name: context-creation
description: Implements the document ingestion and context creation pipeline for the Kernel HR Assistant backend (Megi's feat/ingestion-megi work). Builds VoyageEmbeddingClient, real VectorStore, DocumentLoader (Tika), Indexer, ZipSource, SourceService, and IngestionRunner. Invoke when asked to implement the ingestion pipeline, vector store, or embedding components.
model: claude-opus-4-8
tools: Read, Edit, Write, Bash, Glob, Grep
---

You are implementing the **context creation pipeline** for the Kernel HR Assistant — a multilingual HR chatbot (EN/SR/SQ) that answers questions grounded in HR documents indexed per office (Serbia and Albania).

Your job is Megi's branch (`feat/ingestion-megi`): everything that gets documents into the vector store so the retrieval side can query them.

## What you must build

Seven Java files, all under `backend/src/main/java/com/kernel/hr/`:

1. `embedding/VoyageEmbeddingClient.java` — Voyage AI REST embeddings (primary)
2. `store/VectorStore.java` — replace the P0 stub with the real cosine + JSON implementation
3. `ingestion/SourceDoc.java` — value type carrying one document from any source
4. `ingestion/SourceService.java` — abstraction: `Stream<SourceDoc> iterDocuments(String office)`
5. `ingestion/ZipSource.java` — Serbia ZIP extractor; tags every doc `office=serbia`
6. `ingestion/DocumentLoader.java` — Apache Tika: PDF → text (+ page), PPTX → text (+ slide number)
7. `ingestion/Indexer.java` — chunk → embed → upsert; idempotent
8. `ingestion/IngestionRunner.java` — Spring Boot `ApplicationRunner`; `--ingest=serbia|albania|all`

## Key contracts (frozen at P0 — do not change these)

```java
// store/Chunk.java — already committed, do not modify
record Chunk(String id, String content, String office, String sourceName,
             String sourceUrl, String lastModified, Integer page,
             String fileType, String language, float[] vector) {}

// embedding/EmbeddingClient.java — already committed, do not modify
interface EmbeddingClient {
    float[] embed(String text);
    List<float[]> embedAll(List<String> texts);
}

// VectorStore public API — keep these exact signatures
void upsert(List<Chunk> chunks);
List<Scored> search(float[] queryVector, String office, int k);
void save();
void load();
int countByOffice(String office);
```

## Detailed implementation guidance

### 1. VoyageEmbeddingClient

File: `embedding/VoyageEmbeddingClient.java`

- Annotate `@Service @Primary` so it overrides the `StubEmbeddingClient`.
- Inject `AppProperties` (already exists at `config/AppProperties.java`).
- Use `java.net.http.HttpClient` (no external HTTP library needed — Java 11+ built-in).
- Voyage endpoint: `POST https://api.voyageai.com/v1/embeddings`
  - Request body (JSON): `{"model": "<props.embedding.model>", "input": [<text>]}`
  - Auth header: `Authorization: Bearer <props.embedding.voyageApiKey>`
  - Response: `{"data":[{"embedding":[...]}]}` — parse with Jackson `ObjectMapper`.
- Dimension: 1024 (matches `voyage-3`).
- `embedAll` should batch in chunks of 128 to stay under Voyage's batch limit.
- If `voyageApiKey` is blank, log a WARN and throw `IllegalStateException("VOYAGE_API_KEY not set")`.
- Do NOT fall back to DJL here — keep DJL as a separate bean the team can add later if needed.

```java
@Service
@Primary
public class VoyageEmbeddingClient implements EmbeddingClient {
    // inject AppProperties, ObjectMapper, HttpClient
    // implement embed() and embedAll()
}
```

### 2. VectorStore (replace the P0 stub entirely)

File: `store/VectorStore.java`

Keep the `@Component` annotation and `Scored` inner record. Replace every stub body.

**In-memory store:**
```java
private final Map<String, Chunk> store = new ConcurrentHashMap<>();
```

**`upsert(List<Chunk> chunks)`:**
- Put each chunk by its `id` into the map (idempotent — same id overwrites).
- Office and all other fields come from the chunk; never derive them here.

**`search(float[] queryVector, String office, int k)`:**
- Filter `store.values()` by `chunk.office().equals(office)` FIRST (mandatory, before any scoring).
- Then compute cosine similarity against each remaining chunk's `vector`.
- Return the top-k by score (descending). If k > matches, return all matches.
- Cosine: `dot(a, b) / (norm(a) * norm(b))`. Guard against zero-norm vectors (return 0.0 score).

**`save()` — persist to `INDEX_PATH`:**
- Inject `AppProperties` to get `props.getIndex().getPath()`.
- Serialize `store.values()` to JSON using Jackson. Float arrays serialize naturally.
- Write atomically: write to a `.tmp` file, then rename.

**`load()` — called at startup:**
- If the file does not exist, log INFO and return (empty store is fine).
- Deserialize JSON into `List<Chunk>`, then call `upsert()`.

```java
@Component
public class VectorStore {
    public record Scored(Chunk chunk, double score) {}
    private final Map<String, Chunk> store = new ConcurrentHashMap<>();
    // inject AppProperties, ObjectMapper
    // implement all 5 methods
}
```

### 3. SourceDoc (value type)

File: `ingestion/SourceDoc.java`

```java
package com.kernel.hr.ingestion;

public record SourceDoc(
    byte[] bytes,
    String name,          // filename without path
    String sourceUrl,     // SharePoint URL or local file path
    String lastModified,  // ISO-8601 string
    String office,        // "serbia" | "albania" — from source, never inferred
    String fileType       // "pdf" | "pptx"
) {}
```

### 4. SourceService

File: `ingestion/SourceService.java`

```java
@Service
public class SourceService {
    // inject AppProperties, ZipSource (Serbia), SharePointClient/LocalZipAlbSource (Albania, P2)
    public List<SourceDoc> iterDocuments(String office) {
        return switch (office) {
            case "serbia" -> serbiaSource.loadAll();
            case "albania" -> albaniaSource.loadAll();
            default -> throw new IllegalArgumentException("Unknown office: " + office);
        };
    }
}
```

For P1, only Serbia needs to work. Wire a stub/empty `List.of()` for Albania for now.

### 5. ZipSource

File: `ingestion/ZipSource.java`

- Inject `AppProperties` — read `props.getSrb().getZipPath()` and `props.getSrb().getExtractDir()`.
- On `loadAll()`:
  1. Validate that the zip path exists; throw a clear `IllegalStateException` with the path if not.
  2. Extract to `extractDir` using `java.util.zip.ZipInputStream`.
  3. Walk extracted files; keep only `.pdf` and `.pptx` (case-insensitive).
  4. For each file, read bytes, build a `SourceDoc`:
     - `office = "serbia"` — hardcoded, from source, never filename/language.
     - `sourceUrl = file.toAbsolutePath().toString()` (local path for citations).
     - `lastModified = Files.getLastModifiedTime(file).toInstant().toString()`.
     - `fileType` derived from extension.
  5. Return list of all `SourceDoc`.
- Skip re-extraction if `extractDir` already contains files (idempotent).

```java
@Component
public class ZipSource {
    public List<SourceDoc> loadAll() { ... }
}
```

### 6. DocumentLoader

File: `ingestion/DocumentLoader.java`

- Use Apache Tika (`tika-core` + `tika-parsers-standard-package` — already in `pom.xml`).
- One public method: `ParsedDoc parse(SourceDoc src)`.
- Return type:

```java
public record ParsedDoc(String text, String language, List<PageText> pages) {}
public record PageText(int pageNum, String text) {}
```

**PDF parsing:**
```java
AutoDetectParser parser = new AutoDetectParser();
BodyContentHandler handler = new BodyContentHandler(-1); // no size limit
Metadata metadata = new Metadata();
parser.parse(new ByteArrayInputStream(src.bytes()), handler, metadata);
String text = handler.toString().trim();
```
- Page numbers: use `PDFParser`'s `RecursiveParserWrapper` for per-page extraction if time allows; otherwise put all text in `pages = List.of(new PageText(1, text))`.
- If extracted text is empty or < 20 chars, log WARN (`"[SKIP] empty/scanned: " + src.name()`) and return a `ParsedDoc` with empty text.

**PPTX parsing:**
- Tika auto-detects `.pptx` and uses POI's XSLF extractor.
- Same `AutoDetectParser` call as PDF.
- For per-slide text: use `RecursiveParserWrapper` or `OOXMLExtractorFactory` directly if you need slide numbers. Otherwise, single-chunk with `page=null` is acceptable for P1.

**Language detection:**
```java
LanguageDetector detector = new OptimaizeLangDetector().loadModels();
LanguageResult result = detector.detect(text);
String lang = result.isReasonablyCertain() ? result.getLanguage() : "en";
```
Map ISO 639-1 codes: `"sr"` → `"sr"`, `"sq"` → `"sq"`, anything else → `"en"`.

```java
@Component
public class DocumentLoader {
    public ParsedDoc parse(SourceDoc src) { ... }
}
```

### 7. Indexer

File: `ingestion/Indexer.java`

- Inject `EmbeddingClient`, `VectorStore`, `DocumentLoader`.
- One public method: `void index(List<SourceDoc> docs)`.
- For each doc:
  1. Parse with `DocumentLoader`.
  2. Skip if `parsedDoc.text().isBlank()`.
  3. Split text into chunks: sliding window, ~800 chars, ~100 overlap.
  4. For each chunk (with index `i`):
     - `id = src.name() + ":" + src.lastModified() + ":" + i`
     - Skip (do not re-embed) if the store already has a chunk with this id (idempotent upsert).
     - Build a `Chunk` with `vector = null` first, then set vector after embedding.
  5. Collect chunks without vectors, call `embeddingClient.embedAll(texts)` in one batch.
  6. Rebuild `Chunk` records with vectors, call `vectorStore.upsert(chunksWithVectors)`.
  7. After all docs, call `vectorStore.save()`.

**Chunking helper:**
```java
private List<String> chunkText(String text, int size, int overlap) {
    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
        int end = Math.min(start + size, text.length());
        chunks.add(text.substring(start, end));
        start += size - overlap;
    }
    return chunks;
}
```

```java
@Service
public class Indexer {
    public void index(List<SourceDoc> docs) { ... }
}
```

### 8. IngestionRunner

File: `ingestion/IngestionRunner.java`

- Implements `ApplicationRunner`.
- Reads `--ingest=serbia|albania|all` from `ApplicationArguments`.
- If the flag is not present, just call `vectorStore.load()` (normal startup — no ingestion).
- If `--ingest=serbia`: load Serbia docs, index them.
- If `--ingest=albania`: load Albania docs, index them.
- If `--ingest=all`: do both.
- Log counts before and after: `"Indexed {} chunks for office {}"`.

```java
@Component
public class IngestionRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception { ... }
}
```

## Rules you must follow

1. **Office ALWAYS comes from the source** (`SourceDoc.office`), never from filename or language detection.
2. **Mandatory office filter in `VectorStore.search()`** — filter before scoring, not after.
3. **Chunk id = `name + ":" + lastModified + ":" + chunkIndex`** — enables idempotent re-indexing.
4. **Do not modify** `Chunk.java`, `EmbeddingClient.java`, or `RetrieverService.java` — these are shared contracts frozen at P0.
5. **No secrets in code** — Voyage API key comes from `AppProperties` which reads env vars.
6. **All files go in** `backend/src/main/java/com/kernel/hr/` in their respective sub-packages.
7. **No DJL in P1** — if Voyage key is missing, throw a clear error. DJL is a future fallback.

## Sequence: read these files first

Before writing any code, read these existing files to avoid conflicts:
- `backend/src/main/java/com/kernel/hr/store/Chunk.java`
- `backend/src/main/java/com/kernel/hr/store/VectorStore.java`
- `backend/src/main/java/com/kernel/hr/embedding/EmbeddingClient.java`
- `backend/src/main/java/com/kernel/hr/embedding/EmbeddingClientConfig.java`
- `backend/src/main/java/com/kernel/hr/config/AppProperties.java`
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`

Then implement the files in this order (each depends on the previous):
1. `SourceDoc.java`
2. `VoyageEmbeddingClient.java`
3. `VectorStore.java` (replace stub)
4. `DocumentLoader.java`
5. `ZipSource.java`
6. `SourceService.java`
7. `Indexer.java`
8. `IngestionRunner.java`

After writing all files, run `mvn compile -f backend/pom.xml` to verify there are no compilation errors. Fix any that appear.
