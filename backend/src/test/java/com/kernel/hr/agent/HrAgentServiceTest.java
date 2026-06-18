package com.kernel.hr.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kernel.hr.audit.AuditService;
import com.kernel.hr.config.AppProperties;
import com.kernel.hr.web.dto.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for HrAgentService safe-degradation behaviour.
 */
@ExtendWith(MockitoExtension.class)
class HrAgentServiceTest {

    @Mock private HrTools hrTools;
    @Mock private ScopeGuard scopeGuard;
    @Mock private AuditService auditService;

    private HrAgentService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        // Leave ANTHROPIC_API_KEY blank — default is ""
        service = new HrAgentService(props, hrTools, scopeGuard, auditService, new ObjectMapper());
    }

    @Test
    void blankApiKey_returnsCfgMessage_doesNotThrow() {
        ChatResponse response = assertDoesNotThrow(
                () -> service.answer("How many vacation days?", "en", "albania", "user@test.com"));

        assertTrue(response.text().contains("[CONFIG]"),
                "Response must contain [CONFIG] marker when API key is not set");
        assertFalse(response.refused(),
                "Config message is not a scope refusal — refused must be false");
    }

    @Test
    void blankApiKey_stillAuditsTheTurn() {
        service.answer("How many vacation days?", "en", "albania", "user@test.com");

        // Audit must always fire, even on config errors, so the turn is traceable
        verify(auditService).log(eq("user@test.com"), eq("albania"),
                eq("How many vacation days?"), eq(false), contains("[CONFIG]"));
    }
}
