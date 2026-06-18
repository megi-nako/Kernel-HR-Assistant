package com.kernel.hr.ingestion;

import com.kernel.hr.embedding.EmbeddingClient;
import com.kernel.hr.store.Chunk;
import com.kernel.hr.store.VectorStore;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Megi P3 tests: PPTX parsing, incremental re-index, office tag correctness.
 */
@ExtendWith(MockitoExtension.class)
class IngestionTest {

    @Mock private EmbeddingClient embeddingClient;
    @Mock private VectorStore vectorStore;
    @Mock private DocumentLoader documentLoader;

    private Indexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new Indexer(embeddingClient, vectorStore, documentLoader);
    }

    // --- Test #1: PPTX extraction produces non-empty text ---

    @Test
    void pptx_extractedTextIsNonEmpty() throws Exception {
        DocumentLoader realLoader = new DocumentLoader();
        byte[] pptxBytes = minimalPptx("Annual leave policy: employees are entitled to 20 days of paid vacation per year.");

        SourceDoc src = new SourceDoc(pptxBytes, "HR_Policy.pptx",
                "file://HR_Policy.pptx", "2024-01-01T00:00:00Z", "albania", "pptx");

        DocumentLoader.ParsedDoc result = realLoader.parse(src);

        assertFalse(result.text().isBlank(),
                "Tika must extract non-empty text from a PPTX slide");
        assertTrue(result.text().toLowerCase().contains("vacation")
                        || result.text().toLowerCase().contains("leave")
                        || result.text().toLowerCase().contains("annual"),
                "Extracted text must contain the slide content");
    }

    // --- Test #2: incremental re-index — skips chunks already in the store ---

    @Test
    void indexer_skipsChunks_whenAlreadyIndexed() {
        when(documentLoader.parse(any())).thenReturn(
                new DocumentLoader.ParsedDoc("HR policy content about vacation and benefits. ".repeat(20), "en"));
        // All chunk IDs already present in the store
        when(vectorStore.hasChunk(anyString())).thenReturn(true);

        indexer.index(List.of(serbiaDoc("policy.pdf", "2024-01-01T00:00:00Z")));

        // No embedding call and no upsert when all chunks are current
        verify(embeddingClient, never()).embedAll(any());
        verify(vectorStore, never()).upsert(any());
    }

    @Test
    void indexer_reembeds_whenLastModifiedChanges() {
        String docText = "HR policy content about vacation and annual leave benefits. ".repeat(20);
        when(documentLoader.parse(any())).thenReturn(new DocumentLoader.ParsedDoc(docText, "en"));

        // Phase 1 — original version: all chunks already in store → no embedding
        when(vectorStore.hasChunk(anyString())).thenReturn(true);
        indexer.index(List.of(serbiaDoc("policy.pdf", "2024-01-01T00:00:00Z")));
        verify(embeddingClient, never()).embedAll(any());

        // Phase 2 — updated lastModified produces new chunk IDs → not in store → re-embed
        reset(vectorStore);
        when(vectorStore.hasChunk(anyString())).thenReturn(false);
        when(embeddingClient.embedAll(any())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return Collections.nCopies(texts.size(), new float[1024]);
        });
        indexer.index(List.of(serbiaDoc("policy.pdf", "2024-02-01T00:00:00Z")));
        verify(embeddingClient).embedAll(any());
        verify(vectorStore).upsert(any());
    }

    // --- Test #3: every chunk's office comes from SourceDoc, not filename or language ---

    @Test
    @SuppressWarnings("unchecked")
    void indexer_chunkOffice_fromSourceNotFilenameOrLanguage() {
        // Filename and detected language both suggest Albania, but SourceDoc.office = "serbia"
        when(documentLoader.parse(any())).thenReturn(
                new DocumentLoader.ParsedDoc(
                        // Albanian-language text about HR to make language detection lean SQ
                        "Politika e pushimeve vjetore: punonjësit kanë të drejtë për 20 ditë pushim. ".repeat(20),
                        "sq"));
        when(vectorStore.hasChunk(anyString())).thenReturn(false);
        when(embeddingClient.embedAll(any())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return Collections.nCopies(texts.size(), new float[1024]);
        });

        // Office is "serbia" despite an Albanian-sounding filename and SQ-detected language
        SourceDoc misleadingDoc = new SourceDoc(
                new byte[0],
                "albania_vacation_policy.pdf",
                "file://albania_vacation_policy.pdf",
                "2024-01-01T00:00:00Z",
                "serbia",
                "pdf");

        indexer.index(List.of(misleadingDoc));

        ArgumentCaptor<List<Chunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).upsert(captor.capture());

        List<Chunk> upserted = captor.getValue();
        assertFalse(upserted.isEmpty(), "At least one chunk must be produced");
        upserted.forEach(chunk ->
                assertEquals("serbia", chunk.office(),
                        "Office must come from SourceDoc.office(), not from the filename or detected language"));
    }

    // -------------------------------------------------------------------------

    private SourceDoc serbiaDoc(String name, String lastModified) {
        return new SourceDoc(new byte[0], name, "file://" + name, lastModified, "serbia", "pdf");
    }

    private static byte[] minimalPptx(String slideText) throws Exception {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            XSLFSlide slide = pptx.createSlide();
            XSLFTextBox box = slide.createTextBox();
            XSLFTextRun run = box.addNewTextParagraph().addNewTextRun();
            run.setText(slideText);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pptx.write(out);
            return out.toByteArray();
        }
    }
}
