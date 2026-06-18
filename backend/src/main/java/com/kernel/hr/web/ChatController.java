package com.kernel.hr.web;

import com.kernel.hr.web.dto.ChatRequest;
import com.kernel.hr.web.dto.ChatResponse;
import com.kernel.hr.web.dto.Citation;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

    // P1: inject HrAgentService here (Agathi — feat/agent-agathi)
    // private final HrAgentService agentService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req, HttpSession session) {
        String office = (String) session.getAttribute("office");
        if (office == null) {
            // Should not happen — Spring Security gate rejects unauthenticated requests before here.
            return ResponseEntity.status(401).build();
        }

        // Stub: return a canned response until HrAgentService is wired in P2.
        ChatResponse stub = new ChatResponse(
            "[STUB] Agent not yet wired. Office: " + office + ". Question received: " + req.question(),
            "en",
            List.of(new Citation("stub-document.pdf", "2026-01-01", null, 1)),
            false,
            null
        );
        return ResponseEntity.ok(stub);
    }
}
