package com.kernel.hr.web.dto;

import java.util.List;
import java.util.Map;

// office is NOT a field — it comes from the server session.
// history is optional: [{role:"user"|"assistant", content:"..."}] from the client.
public record ChatRequest(
    String question,
    List<Map<String, String>> history
) {}
