package com.kernel.hr.store;

// Contract A — frozen at P0; change only by group agreement.
public record Chunk(
    String id,          // sourceId + ":" + lastModified + ":" + chunkIndex
    String content,
    String office,      // "serbia" | "albania" — from SOURCE, never from filename/language
    String sourceName,
    String sourceUrl,
    String lastModified,
    Integer page,       // page number (PDF) or slide number (PPTX), null if unknown
    String fileType,    // "pdf" | "pptx"
    String language,    // detected language of this chunk (en/sr/sq)
    float[] vector      // embedding — null before indexing
) {}
