package com.kernel.hr.web;

import com.kernel.hr.agent.HrAgentService;
import com.kernel.hr.util.LanguageDetector;
import com.kernel.hr.web.dto.ChatRequest;
import com.kernel.hr.web.dto.ChatResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final HrAgentService agentService;
    private final LanguageDetector languageDetector;

    public ChatController(HrAgentService agentService, LanguageDetector languageDetector) {
        this.agentService = agentService;
        this.languageDetector = languageDetector;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req, HttpSession session) {
        String office = (String) session.getAttribute("office");
        String upn = (String) session.getAttribute("upn");
        if (office == null) {
            return ResponseEntity.status(401).build();
        }
        String lang = languageDetector.detect(req.question());
        List<Map<String, String>> history =
                req.history() != null ? req.history() : List.of();
        ChatResponse response = agentService.answer(req.question(), lang, office, upn, history);
        return ResponseEntity.ok(response);
    }
}
