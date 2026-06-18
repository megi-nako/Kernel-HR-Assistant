package com.kernel.hr.web;

import com.kernel.hr.store.VectorStore;
import com.kernel.hr.web.dto.StatusResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatusController {

    private final VectorStore vectorStore;

    public StatusController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @GetMapping("/status")
    public StatusResponse status(HttpSession session) {
        String office = session != null ? (String) session.getAttribute("office") : "unknown";
        if (office == null) office = "unknown";
        int docCount = vectorStore.countByOffice(office);
        return new StatusResponse(office, docCount, null);
    }
}
