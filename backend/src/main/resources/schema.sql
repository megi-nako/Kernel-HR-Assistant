-- Enable pgvector extension. Succeeds on pgvector/pgvector:pg17 Docker image.
-- Must run before the CREATE TABLE below because the vector(1024) column type
-- depends on it.
CREATE EXTENSION IF NOT EXISTS vector;

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
    vector        vector(1024)
);

-- B-tree on office: applied as a hard filter BEFORE the vector similarity scan.
CREATE INDEX IF NOT EXISTS idx_chunks_office ON chunks (office);

-- IVFFlat ANN index for cosine search. Effective above ~1 000 rows.
-- Rebuild with DROP/CREATE after a full re-ingestion if recall degrades.
CREATE INDEX IF NOT EXISTS idx_chunks_vector_cosine
    ON chunks USING ivfflat (vector vector_cosine_ops)
    WITH (lists = 100);
