package com.kernel.hr.agent;

import com.kernel.hr.retrieval.RetrieverService;
import com.kernel.hr.store.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P3 tests: office isolation and HR scope enforcement.
 *
 * ScopeGuard is tested as a unit — no Spring context required.
 * RetrieverService and VectorStore are mocked to control the retrieval outcome.
 */
@ExtendWith(MockitoExtension.class)
class ScopeGuardTest {

    @Mock private RetrieverService retrieverService;
    @Mock private VectorStore vectorStore;

    private ScopeGuard scopeGuard;

    @BeforeEach
    void setUp() {
        scopeGuard = new ScopeGuard(retrieverService, vectorStore, 0.35);
    }

    // --- Test #2: off-topic query → refused (scope gate triggers) ---

    @Test
    void offTopicQuery_isRefusedWhenIndexHasData() {
        // Index is non-empty → ScopeGuard must not bypass
        when(vectorStore.countByOffice("albania")).thenReturn(5);
        // Weather question retrieves nothing useful → empty result list
        when(retrieverService.retrieve(anyString(), eq("albania"), anyInt()))
                .thenReturn(List.of());

        Optional<String> result = scopeGuard.check("What is the weather today?", "albania");

        assertTrue(result.isPresent(), "Off-topic query must be refused");
        assertTrue(result.get().contains("albania"),
                "Refusal message must mention the office");
    }

    @Test
    void emptyIndex_bypassesGate() {
        when(vectorStore.countByOffice("albania")).thenReturn(0);

        Optional<String> result = scopeGuard.check("What is the weather today?", "albania");

        assertFalse(result.isPresent(),
                "ScopeGuard must bypass when the index is not yet built");
        // retrieve is never called when index is empty — no wasted embedding call
        verify(retrieverService, never()).retrieve(any(), any(), anyInt());
    }

    // --- Test #3: Serbia user asking in Albanian → only Serbia store queried ---

    @Test
    void serbiaUser_albanianLanguageQuestion_queriesOnlySerbiaStore() {
        // Serbia office has indexed documents
        when(vectorStore.countByOffice("serbia")).thenReturn(3);
        // Return empty → ScopeGuard will refuse, but the critical invariant
        // is that Albania's store was never touched regardless of language
        when(retrieverService.retrieve(anyString(), eq("serbia"), anyInt()))
                .thenReturn(List.of());

        // Albanian-language HR question from a Serbia-office user
        scopeGuard.check("Sa ditë pushimi kam në vit?", "serbia");

        // Office isolation: Albania store must never be queried
        verify(vectorStore, never()).countByOffice("albania");
        verify(retrieverService, never()).retrieve(any(), eq("albania"), anyInt());
    }
}
