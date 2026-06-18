package com.kernel.hr.web.dto;

// Contract D — frozen at P0.
public record Citation(
    String sourceName,
    String lastModified,
    String url,
    Integer page
) {}
