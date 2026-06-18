package com.kernel.hr.util;

import org.apache.tika.language.detect.LanguageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LanguageDetector {

    private static final Logger log = LoggerFactory.getLogger(LanguageDetector.class);
    private static final int MIN_TEXT_LENGTH = 10;
    private static final Set<String> SUPPORTED = Set.of("en", "sr", "sq");

    // Albanian SQ keywords (common function words)
    private static final Set<String> SQ_WORDS = Set.of(
        "dhe", "një", "për", "me", "nga", "çfarë", "si", "kur", "ditë", "pushime",
        "punonjës", "kompani", "rregullore", "politika", "lejen", "shëndetit");

    // Serbian SR keywords (common function words)
    private static final Set<String> SR_WORDS = Set.of(
        "kako", "što", "koji", "koja", "koje", "dana", "radnog", "odmor", "zaposleni",
        "kompanija", "pravilnik", "godišnji", "bolovanje", "zahtev", "nije", "sam");

    public String detect(String text) {
        if (text == null || text.trim().length() < MIN_TEXT_LENGTH) {
            return "en";
        }

        // Try Tika's service-loader based detector first
        try {
            org.apache.tika.language.detect.LanguageDetector tikaDetector =
                org.apache.tika.language.detect.LanguageDetector.getDefaultLanguageDetector();
            if (tikaDetector != null) {
                tikaDetector.reset();
                tikaDetector.addText(text);
                LanguageResult result = tikaDetector.detect();
                if (result != null && !result.isUnknown()) {
                    String lang = result.getLanguage();
                    if (SUPPORTED.contains(lang)) return lang;
                }
            }
        } catch (Exception e) {
            log.debug("Tika language detection unavailable, using keyword fallback: {}", e.getMessage());
        }

        // Keyword fallback for EN / SR / SQ
        return keywordDetect(text.toLowerCase());
    }

    private String keywordDetect(String lower) {
        String[] words = lower.split("[\\s,;.!?]+");
        int sqCount = 0, srCount = 0;
        for (String w : words) {
            if (SQ_WORDS.contains(w)) sqCount++;
            if (SR_WORDS.contains(w)) srCount++;
        }
        if (sqCount == 0 && srCount == 0) return "en";
        if (sqCount >= srCount) return "sq";
        return "sr";
    }
}
