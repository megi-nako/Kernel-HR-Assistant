package com.kernel.hr.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

// Body for POST /api/conversations. office/upn are NOT fields — they come from
// the server session. messages is the raw frontend message array (stored as JSONB).
public record SaveConversationRequest(String id, String title, JsonNode messages) {}
