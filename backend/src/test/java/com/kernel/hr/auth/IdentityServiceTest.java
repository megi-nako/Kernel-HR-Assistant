package com.kernel.hr.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kernel.hr.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for office resolution and internal-member gate.
 * Covers SPEC.md §9 #1 (office isolation) and #3 (internal employees only).
 */
class IdentityServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = new IdentityService(new AppProperties());
    }

    // --- resolveOffice: company displayName ---

    @Test
    void resolveOffice_albaniaCompanyName_returnsAlbania() throws Exception {
        JsonNode profile = profile("Engineering Albania", "Tirana", "internalMember");
        assertEquals("albania", service.resolveOffice(profile));
    }

    @Test
    void resolveOffice_serbiaCompanyName_returnsSerbia() throws Exception {
        JsonNode profile = profile("Engineering Serbia", "Beograd", "internalMember");
        assertEquals("serbia", service.resolveOffice(profile));
    }

    // --- resolveOffice: city fallback ---

    @Test
    void resolveOffice_tiranaCity_returnsAlbania() throws Exception {
        JsonNode profile = profile("Engineering EU", "Tirana", "internalMember");
        assertEquals("albania", service.resolveOffice(profile));
    }

    @Test
    void resolveOffice_belgradCity_returnsSerbia() throws Exception {
        JsonNode profile = profile("Engineering EU", "Belgrade", "internalMember");
        assertEquals("serbia", service.resolveOffice(profile));
    }

    @Test
    void resolveOffice_beogradCity_returnsSerbia() throws Exception {
        JsonNode profile = profile("Engineering EU", "Beograd", "internalMember");
        assertEquals("serbia", service.resolveOffice(profile));
    }

    // --- resolveOffice: fail-closed ---

    @Test
    void resolveOffice_unknownCompanyAndCity_returnsNull() throws Exception {
        JsonNode profile = profile("Acme Corp", "London", "internalMember");
        assertNull(service.resolveOffice(profile),
                "Unknown company and city must return null — fail-closed");
    }

    @Test
    void resolveOffice_noCurrentPosition_returnsNull() throws Exception {
        JsonNode profile = MAPPER.readTree("""
                {
                  "displayName": "Someone",
                  "account": [{"userPrincipalName": "x@x.com", "userPersona": "internalMember"}],
                  "positions": [
                    {
                      "isCurrent": false,
                      "detail": {"company": {"displayName": "Engineering Albania"}}
                    }
                  ]
                }
                """);
        assertNull(service.resolveOffice(profile),
                "Non-current position must not be used for office resolution");
    }

    // --- loadUser: internal-member gate ---

    @Test
    void loadUser_internalMember_returnsUser() throws Exception {
        JsonNode profile = profile("Engineering Albania", "Tirana", "internalMember");
        IdentityService.User user = service.loadUser(profile);
        assertEquals("albania", user.office());
        assertTrue(user.internal());
    }

    @Test
    void loadUser_nonInternalMember_throws() throws Exception {
        JsonNode profile = profile("Engineering Albania", "Tirana", "contractor");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.loadUser(profile));
        assertTrue(ex.getMessage().toLowerCase().contains("internal"),
                "Error message must mention the internal-member requirement");
    }

    @Test
    void loadUser_unresolvableOffice_throws() throws Exception {
        JsonNode profile = profile("Acme Corp", "London", "internalMember");
        assertThrows(IllegalArgumentException.class, () -> service.loadUser(profile),
                "Unresolvable office must deny access (fail-closed)");
    }

    // -------------------------------------------------------------------------

    private JsonNode profile(String companyName, String city, String userPersona) throws Exception {
        return MAPPER.readTree("""
                {
                  "displayName": "Test User",
                  "account": [
                    {
                      "userPrincipalName": "test@kernel.com",
                      "userPersona": "%s"
                    }
                  ],
                  "positions": [
                    {
                      "isCurrent": true,
                      "detail": {
                        "company": {
                          "displayName": "%s",
                          "address": { "city": "%s" }
                        }
                      }
                    }
                  ]
                }
                """.formatted(userPersona, companyName, city));
    }
}
