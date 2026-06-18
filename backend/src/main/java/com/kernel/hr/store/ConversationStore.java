package com.kernel.hr.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Persists chat conversations to PostgreSQL. The message list is stored as a
 * JSONB array exactly as the frontend produces it, so a conversation survives a
 * page refresh or re-login without a separate per-message schema.
 *
 * Every read/write is scoped by {@code upn} — a user only ever sees their own
 * conversations.
 */
@Component
public class ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationStore.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ConversationStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Lightweight row for the sidebar list (no messages). */
    public record Summary(String id, String title, String updatedAt) {}

    /** Full conversation including the message array. */
    public record Detail(String id, String title, JsonNode messages) {}

    public List<Summary> listByUpn(String upn) {
        return jdbc.query("""
                SELECT id, title, updated_at
                FROM   conversations
                WHERE  upn = ?
                ORDER  BY updated_at DESC
                """,
                (rs, n) -> new Summary(
                        rs.getString("id"),
                        rs.getString("title"),
                        toIso(rs.getTimestamp("updated_at"))),
                upn);
    }

    public Optional<Detail> get(String id, String upn) {
        List<Detail> rows = jdbc.query("""
                SELECT id, title, messages::text AS messages
                FROM   conversations
                WHERE  id = ? AND upn = ?
                """,
                (rs, n) -> new Detail(
                        rs.getString("id"),
                        rs.getString("title"),
                        parse(rs.getString("messages"))),
                id, upn);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Insert or update a conversation. The update only applies when the row is
     * owned by the same {@code upn}, so a guessed id cannot overwrite another
     * user's conversation.
     */
    public void upsert(String id, String upn, String office, String title, JsonNode messages) {
        jdbc.update("""
                INSERT INTO conversations (id, upn, office, title, messages, updated_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), now())
                ON CONFLICT (id) DO UPDATE SET
                    title      = EXCLUDED.title,
                    messages   = EXCLUDED.messages,
                    updated_at = now()
                WHERE conversations.upn = EXCLUDED.upn
                """,
                id, upn, office, title, write(messages));
        log.debug("Upserted conversation {} for upn={}", id, upn);
    }

    // -------------------------------------------------------------------------

    private String toIso(Timestamp ts) {
        return ts == null ? null : ts.toInstant().toString();
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "[]" : json);
        } catch (Exception e) {
            log.warn("Failed to parse conversation messages, returning empty: {}", e.getMessage());
            return objectMapper.createArrayNode();
        }
    }

    private String write(JsonNode messages) {
        try {
            return messages == null ? "[]" : objectMapper.writeValueAsString(messages);
        } catch (Exception e) {
            return "[]";
        }
    }
}
