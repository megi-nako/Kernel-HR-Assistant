package com.kernel.hr.ingestion;

public record SourceDoc(
    byte[] bytes,
    String name,
    String sourceUrl,
    String lastModified,
    String office,        // "serbia" | "albania" — ALWAYS from source, never inferred
    String fileType       // "pdf" | "pptx"
) {}
