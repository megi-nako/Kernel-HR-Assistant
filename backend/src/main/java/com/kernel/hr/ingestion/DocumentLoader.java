package com.kernel.hr.ingestion;

import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
public class DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);
    private static final int MIN_TEXT_LENGTH = 20;

    public record ParsedDoc(String text, String language) {}

    public ParsedDoc parse(SourceDoc src) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            parser.parse(new ByteArrayInputStream(src.bytes()), handler, metadata, context);
            String text = handler.toString().trim();

            if (text.isBlank() || text.length() < MIN_TEXT_LENGTH) {
                log.warn("Document {} produced insufficient text (length={}); skipping.", src.name(), text.length());
                return new ParsedDoc("", "en");
            }

            String language = detectLanguage(text, src.name());
            return new ParsedDoc(text, language);

        } catch (Exception e) {
            log.warn("Failed to parse document {}: {}", src.name(), e.getMessage());
            return new ParsedDoc("", "en");
        }
    }

    private String detectLanguage(String text, String docName) {
        try {
            LanguageDetector detector = new OptimaizeLangDetector().loadModels();
            LanguageResult result = detector.detect(text);
            if (result != null && result.isReasonablyCertain()) {
                String lang = result.getLanguage();
                return switch (lang) {
                    case "sr" -> "sr";
                    case "sq" -> "sq";
                    default -> "en";
                };
            }
        } catch (Exception e) {
            log.warn("Language detection failed for {}: {}", docName, e.getMessage());
        }
        return "en";
    }
}
