-- pgvector extension is enabled programmatically in VectorStoreInitializer.java
-- with a clear error message if it is not installed on the OS.

CREATE TABLE IF NOT EXISTS chunks (
    id            VARCHAR(512)  PRIMARY KEY,
    content       TEXT          NOT NULL,
    office        VARCHAR(50)   NOT NULL,
    source_name   VARCHAR(512),
    source_url    TEXT,
    last_modified VARCHAR(64),
    page          INTEGER,
    file_type     VARCHAR(10),
    language      VARCHAR(10),
    vector        vector(1024)  -- 1024-dim Voyage AI embeddings (voyage-3)
);

CREATE INDEX IF NOT EXISTS idx_chunks_office ON chunks (office);

-- IVFFlat index for cosine ANN search. Rebuild after large ingestion runs.
-- Safe to skip for < 1000 rows (sequential scan is faster at small scale).
CREATE INDEX IF NOT EXISTS idx_chunks_vector_cosine
    ON chunks USING ivfflat (vector vector_cosine_ops)
    WITH (lists = 100);
