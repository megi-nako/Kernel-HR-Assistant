package com.kernel.hr.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Vector store backed by PostgreSQL + pgvector.
 *
 * Persistence is handled by PostgreSQL — save() and load() are no-ops kept for
 * interface compatibility with IngestionRunner.
 *
 * Requires: pgvector extension installed, schema.sql applied (runs automatically on startup).
 */
@Component
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);

    public record Scored(Chunk chunk, double score) {}

    private final JdbcTemplate jdbc;

    public VectorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void upsert(List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            jdbc.update("""
                    INSERT INTO chunks
                        (id, content, office, source_name, source_url,
                         last_modified, page, file_type, language, vector)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                    ON CONFLICT (id) DO UPDATE SET
                        content       = EXCLUDED.content,
                        last_modified = EXCLUDED.last_modified,
                        vector        = EXCLUDED.vector
                    """,
                    chunk.id(),
                    chunk.content(),
                    chunk.office(),
                    chunk.sourceName(),
                    chunk.sourceUrl(),
                    chunk.lastModified(),
                    chunk.page(),
                    chunk.fileType(),
                    chunk.language(),
                    toVectorLiteral(chunk.vector())
            );
        }
        log.debug("Upserted {} chunks", chunks.size());
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the top-k chunks for the given office, ranked by cosine similarity.
     * The office filter is applied BEFORE the vector comparison — other-office rows
     * never enter the candidate set.
     */
    public List<Scored> search(float[] queryVector, String office, int k) {
        String q = toVectorLiteral(queryVector);
        return jdbc.query("""
                SELECT id, content, office, source_name, source_url,
                       last_modified, page, file_type, language, vector,
                       1 - (vector <=> CAST(? AS vector)) AS score
                FROM   chunks
                WHERE  office = ?
                  AND  vector IS NOT NULL
                ORDER  BY vector <=> CAST(? AS vector)
                LIMIT  ?
                """,
                (rs, rowNum) -> new Scored(mapRow(rs), rs.getDouble("score")),
                q, office, q, k
        );
    }

    public boolean hasChunk(String id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM chunks WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public int countByOffice(String office) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM chunks WHERE office = ?", Integer.class, office);
        return count != null ? count : 0;
    }

    // -------------------------------------------------------------------------
    // No-ops — PostgreSQL handles persistence natively
    // -------------------------------------------------------------------------

    public void save() {
        log.debug("save() is a no-op — PostgreSQL persists automatically.");
    }

    public void load() {
        log.debug("load() is a no-op — PostgreSQL persists automatically.");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String toVectorLiteral(float[] vector) {
        if (vector == null) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private float[] parseVectorLiteral(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("[")) s = s.substring(1, s.length() - 1);
        String[] parts = s.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    private Chunk mapRow(ResultSet rs) throws SQLException {
        return new Chunk(
                rs.getString("id"),
                rs.getString("content"),
                rs.getString("office"),
                rs.getString("source_name"),
                rs.getString("source_url"),
                rs.getString("last_modified"),
                (Integer) rs.getObject("page"),
                rs.getString("file_type"),
                rs.getString("language"),
                parseVectorLiteral(rs.getString("vector"))
        );
    }
}
