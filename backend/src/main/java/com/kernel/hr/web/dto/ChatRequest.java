package com.kernel.hr.web.dto;

// Contract D — frozen at P0. office is NOT a field — it comes from the server session.
public record ChatRequest(String question) {}
