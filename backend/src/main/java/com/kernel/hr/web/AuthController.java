package com.kernel.hr.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kernel.hr.auth.IdentityService;
import com.kernel.hr.config.AppProperties;
import com.kernel.hr.web.dto.LoginRequest;
import com.kernel.hr.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final IdentityService identityService;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public AuthController(IdentityService identityService,
                          AppProperties props,
                          ObjectMapper objectMapper) {
        this.identityService = identityService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest httpReq) {
        try {
            IdentityService.User user = resolveUser(req.username());

            // Create authentication and store in session
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    user.upn(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
            authentication.setDetails(user);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession session = httpReq.getSession(true);
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            session.setAttribute("office", user.office());
            session.setAttribute("upn", user.upn());

            return ResponseEntity.ok(new LoginResponse(user.upn(), user.displayName(), user.office()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users")
    public ResponseEntity<List<String>> listUsers() {
        return ResponseEntity.ok(identityService.listMockUsers());
    }

    private IdentityService.User resolveUser(String username) throws Exception {
        if ("mock".equals(props.getAuth().getMode())) {
            File profileFile = new File(
                props.getAuth().getMockProfilePath(), username + ".json");
            if (!profileFile.exists()) {
                throw new IllegalArgumentException("Mock profile not found: " + username);
            }
            JsonNode profile = objectMapper.readTree(profileFile);
            return identityService.loadUser(profile);
        }
        throw new UnsupportedOperationException("Entra SSO not yet implemented — use AUTH_MODE=mock");
    }
}
