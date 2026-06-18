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

-- Persisted chat history. One row per conversation, owned by a user (upn).
-- The full message list is stored as a JSONB array exactly as the frontend
-- renders it (role, text, time, language, citations, refused, reason), so a
-- conversation round-trips without a separate messages table.
CREATE TABLE IF NOT EXISTS conversations (
    id         VARCHAR(64)  PRIMARY KEY,
    upn        VARCHAR(256) NOT NULL,
    office     VARCHAR(50),
    title      VARCHAR(512),
    messages   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- List a user's conversations newest-first.
CREATE INDEX IF NOT EXISTS idx_conversations_upn ON conversations (upn, updated_at DESC);
