package com.kernel.hr.web.dto;

import java.util.List;

// Contract D — frozen at P0.
public record ChatResponse(
    String text,
    String language,
    List<Citation> citations,
    boolean refused,
    String reason
) {}
