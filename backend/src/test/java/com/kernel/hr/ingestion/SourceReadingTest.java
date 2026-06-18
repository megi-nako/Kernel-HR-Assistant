package com.kernel.hr.ingestion;

import com.kernel.hr.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the document-reading sources (ZIP, local ZIP, plain folder) against the REAL
 * hackathon data, with no Spring context, no Postgres, no embeddings and no SharePoint creds.
 *
 * The two real archives are read from the user's Downloads folder. If they are not present
 * (e.g. on CI / another machine) the data-dependent tests skip instead of failing.
 */
class SourceReadingTest {

    private static final Path SRB_ZIP =
            Paths.get(System.getProperty("user.home"), "Downloads", "HR DOCUMENTS - SRB 1.zip");
    private static final Path ALB_ZIP = Paths.get(System.getProperty("user.home"), "Downloads",
            "Politikat dhe rregulloret e reja të Engineering Albania.zip");

    static boolean srbZipPresent() { return Files.exists(SRB_ZIP); }
    static boolean albZipPresent() { return Files.exists(ALB_ZIP); }

    // ---------------------------------------------------------------------
    // 1. ZIP reading (Serbia) — ZipSource extracts + walks + tags office
    // ---------------------------------------------------------------------
    @Test
    @EnabledIf("srbZipPresent")
    void zipSource_readsSerbiaArchive(@TempDir Path extractDir) {
        AppProperties props = new AppProperties();
        props.getSrb().setZipPath(SRB_ZIP.toString());
        props.getSrb().setExtractDir(extractDir.toString());

        List<SourceDoc> docs = new ZipSource(props).loadAll();

        assertFalse(docs.isEmpty(), "Serbia ZIP should yield documents");
        assertEquals(23, docs.size(), "Serbia ZIP contains 23 PDF/PPTX files (the dir entry is skipped)");
        assertTrue(docs.stream().allMatch(d -> d.office().equals("serbia")),
                "every Serbia doc must be tagged office=serbia");
        assertTrue(docs.stream().allMatch(d -> d.bytes().length > 0), "every doc must have bytes");
        assertTrue(docs.stream().allMatch(d -> d.fileType().equals("pdf") || d.fileType().equals("pptx")));

        // Tika must extract real text from at least one document
        DocumentLoader loader = new DocumentLoader();
        boolean anyTextExtracted = docs.stream()
                .map(loader::parse)
                .anyMatch(p -> !p.text().isBlank());
        assertTrue(anyTextExtracted, "Tika should extract text from at least one Serbia PDF");

        System.out.printf("[ZIP/Serbia] loaded %d docs, e.g. '%s'%n",
                docs.size(), docs.get(0).name());
    }

    // ---------------------------------------------------------------------
    // 2. Local ZIP reading (Albania fallback) — LocalZipAlbaniaSource
    // ---------------------------------------------------------------------
    @Test
    @EnabledIf("albZipPresent")
    void localZipAlbaniaSource_readsAlbaniaArchive() {
        AppProperties props = new AppProperties();
        props.getAlb().setZipPath(ALB_ZIP.toString());

        List<SourceDoc> docs = new LocalZipAlbaniaSource(props).loadAll();

        assertFalse(docs.isEmpty(), "Albania ZIP should yield documents");
        assertEquals(4, docs.size(), "Albania ZIP is known to contain 4 PDFs");
        assertTrue(docs.stream().allMatch(d -> d.office().equals("albania")),
                "every Albania doc must be tagged office=albania");

        DocumentLoader loader = new DocumentLoader();
        DocumentLoader.ParsedDoc parsed = loader.parse(docs.get(0));
        assertFalse(parsed.text().isBlank(), "Tika should extract text from an Albania PDF");

        System.out.printf("[ZIP/Albania] loaded %d docs, first parsed lang=%s%n",
                docs.size(), parsed.language());
    }

    // ---------------------------------------------------------------------
    // 3. Plain folder reading — LocalFolderSource (new "folder" backend)
    //    Extract the real archive into a temp folder, then read it as a folder.
    // ---------------------------------------------------------------------
    @Test
    @EnabledIf("albZipPresent")
    void localFolderSource_readsPlainFolder(@TempDir Path folder) throws Exception {
        // Arrange: lay real PDFs into a normal folder (no zip involved at read time)
        extractZip(ALB_ZIP, folder);

        List<SourceDoc> docs = new LocalFolderSource().loadAll(folder.toString(), "albania");

        assertEquals(4, docs.size(), "folder should expose the same 4 PDFs");
        assertTrue(docs.stream().allMatch(d -> d.office().equals("albania")));
        assertTrue(docs.stream().allMatch(d -> d.fileType().equals("pdf")));
        assertTrue(docs.stream().allMatch(d -> d.sourceUrl().toLowerCase().endsWith(".pdf")),
                "folder source uses the local file path as the citation URL");

        System.out.printf("[Folder] loaded %d docs from %s%n", docs.size(), folder);
    }

    // ---------------------------------------------------------------------
    // 4. SharePoint (Albania, production) — cannot hit Graph without real creds,
    //    so verify the offline path: it fails fast with a clear config error.
    // ---------------------------------------------------------------------
    @Test
    void sharePointClient_withoutCredentials_failsFast() {
        AppProperties props = new AppProperties(); // all Graph/sharepoint values blank
        SharePointClient client = new SharePointClient(props, new com.fasterxml.jackson.databind.ObjectMapper());
        IllegalStateException ex = assertThrows(IllegalStateException.class, client::loadAll);
        assertTrue(ex.getMessage().contains("GRAPH_"),
                "missing-creds error should name the required GRAPH_* env vars");
    }

    @Test
    void localFolderSource_missingPathFailsClearly() {
        LocalFolderSource src = new LocalFolderSource();
        assertThrows(IllegalStateException.class, () -> src.loadAll("", "albania"));
        assertThrows(IllegalStateException.class,
                () -> src.loadAll("/no/such/folder/here", "albania"));
    }

    // --- helper: minimal unzip into a flat folder ---
    private static void extractZip(Path zip, Path dest) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path out = dest.resolve(entry.getName());
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
}
