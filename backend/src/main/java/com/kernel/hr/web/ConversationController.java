package com.kernel.hr.web;

import com.kernel.hr.store.ConversationStore;
import com.kernel.hr.web.dto.SaveConversationRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Persisted chat history. upn + office are read from the server session — never
// the request body — so a user can only list/read/write their own conversations.
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationStore store;

    public ConversationController(ConversationStore store) {
        this.store = store;
    }

    @GetMapping
    public ResponseEntity<List<ConversationStore.Summary>> list(HttpSession session) {
        String upn = upn(session);
        if (upn == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(store.listByUpn(upn));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationStore.Detail> get(@PathVariable String id, HttpSession session) {
        String upn = upn(session);
        if (upn == null) return ResponseEntity.status(401).build();
        return store.get(id, upn)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody SaveConversationRequest req, HttpSession session) {
        String upn = upn(session);
        if (upn == null) return ResponseEntity.status(401).build();
        if (req.id() == null || req.id().isBlank()) {
            return ResponseEntity.badRequest().body("id is required");
        }
        String office = (String) session.getAttribute("office");
        store.upsert(req.id(), upn, office, req.title(), req.messages());
        return ResponseEntity.ok().build();
    }

    private String upn(HttpSession session) {
        return session == null ? null : (String) session.getAttribute("upn");
    }
}
