package com.kernel.hr.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.kernel.hr.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

// Contract C — frozen at P0.
@Service
public class IdentityService {

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    // Contract C record — frozen at P0.
    public record User(String upn, String displayName, String office, boolean internal) {}

    private final AppProperties props;

    public IdentityService(AppProperties props) {
        this.props = props;
    }

    /**
     * Resolve office from a Microsoft Graph /me/profile JSON.
     * Returns "albania" | "serbia" | null (fail-closed — null means deny access).
     */
    public String resolveOffice(JsonNode profile) {
        try {
            JsonNode positions = profile.path("positions");
            for (JsonNode pos : positions) {
                if (!pos.path("isCurrent").asBoolean(false)) continue;
                JsonNode company = pos.path("detail").path("company");
                String displayName = company.path("displayName").asText("").toLowerCase(Locale.ROOT);
                if (displayName.contains("albania")) return "albania";
                if (displayName.contains("serbia") || displayName.contains("srbija")) return "serbia";
                // city fallback
                String city = company.path("address").path("city").asText("").toLowerCase(Locale.ROOT);
                if (city.contains("tirana")) return "albania";
                if (city.contains("beograd") || city.contains("belgrade")) return "serbia";
            }
        } catch (Exception e) {
            log.warn("Failed to resolve office from profile: {}", e.getMessage());
        }
        return null; // fail-closed
    }

    /**
     * Load and validate a user from a Graph profile JSON.
     * Throws IllegalArgumentException if the user is not an internal member
     * or if office cannot be resolved.
     */
    public User loadUser(JsonNode profile) {
        JsonNode accounts = profile.path("account");
        if (!accounts.isArray() || accounts.isEmpty()) {
            throw new IllegalArgumentException("No account found in profile");
        }
        JsonNode account = accounts.get(0);
        String userPersona = account.path("userPersona").asText("");
        if (!"internalMember".equals(userPersona)) {
            throw new IllegalArgumentException("Access denied: not an internal member");
        }
        String upn = account.path("userPrincipalName").asText("");
        String displayName = profile.path("displayName").asText(upn);
        String office = resolveOffice(profile);
        if (office == null) {
            throw new IllegalArgumentException("Access denied: could not determine office from profile");
        }
        return new User(upn, displayName, office, true);
    }

    /**
     * List available mock usernames (filenames without .json from mock profile directory).
     */
    public List<String> listMockUsers() {
        File dir = new File(props.getAuth().getMockProfilePath());
        if (!dir.exists() || !dir.isDirectory()) return List.of();
        String[] files = dir.list((d, name) -> name.endsWith(".json"));
        if (files == null) return List.of();
        return Arrays.stream(files)
            .map(f -> f.replace(".json", ""))
            .sorted()
            .collect(Collectors.toList());
    }
}
