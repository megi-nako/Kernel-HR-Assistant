package com.kernel.hr.audit;

import com.kernel.hr.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Instant;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_QUESTION_LOG = 200;
    private static final int MAX_RESPONSE_LOG = 300;

    private final AppProperties props;
    private File auditFile;

    public AuditService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        String indexPath = props.getIndex().getPath();
        File indexFile = new File(indexPath);
        auditFile = new File(indexFile.getParentFile(), "audit.log");
        try {
            auditFile.getParentFile().mkdirs();
            log.info("Audit log: {}", auditFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Could not prepare audit log directory: {}", e.getMessage());
        }
    }

    public synchronized void log(String upn, String office, String question,
                                  boolean answered, String response) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(auditFile, true))) {
            String q = sanitize(question, MAX_QUESTION_LOG);
            String r = sanitize(response, MAX_RESPONSE_LOG);
            pw.printf("%s | %s | %s | %s | Q: %s | A: %s%n",
                Instant.now(), upn, office, answered ? "answered" : "refused", q, r);
        } catch (Exception e) {
            log.warn("Audit log write failed: {}", e.getMessage());
        }
    }

    private String sanitize(String text, int maxLen) {
        if (text == null || text.isBlank()) return "-";
        String clean = text.replace("\n", " ").replace("|", "/");
        return clean.length() > maxLen ? clean.substring(0, maxLen) + "…" : clean;
    }
}
