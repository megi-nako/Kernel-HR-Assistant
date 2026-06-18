package com.kernel.hr.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for multilingual detection — the core differentiator of the assistant.
 * Short/ambiguous text falls back to keyword matching; unsupported languages → "en".
 */
class LanguageDetectorTest {

    private LanguageDetector detector;

    @BeforeEach
    void setUp() {
        detector = new LanguageDetector();
    }

    @Test
    void shortText_returnsEnglish() {
        assertEquals("en", detector.detect("Hi"));
        assertEquals("en", detector.detect(null));
        assertEquals("en", detector.detect(""));
    }

    @Test
    void albanianKeywords_returnsSq() {
        // Real Albanian HR question: "How many vacation days do I have?"
        assertEquals("sq", detector.detect("Sa ditë pushime kam dhe çfarë politika ka kompania?"));
    }

    @Test
    void serbianKeywords_returnsSr() {
        // Real Serbian HR question: "How many annual leave days do I have?"
        assertEquals("sr", detector.detect("Koliko dana godišnjeg odmora imam i kako da podnesem zahtev?"));
    }

    @Test
    void englishText_returnsEn() {
        assertEquals("en", detector.detect(
                "How many vacation days am I entitled to under the company policy?"));
    }

    @Test
    void unsupportedLanguage_fallsBackToEnglish() {
        // French text — not in supported set (en/sr/sq), should default to "en"
        assertEquals("en", detector.detect(
                "Combien de jours de congé puis-je prendre par année selon la politique?"));
    }
}
