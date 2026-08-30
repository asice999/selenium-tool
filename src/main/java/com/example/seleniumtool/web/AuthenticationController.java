package com.example.seleniumtool.web;

import com.example.seleniumtool.service.UserAccountService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final UserAccountService userAccountService;

    public AuthenticationController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/status")
    public Map<String, Object> status(Authentication authentication, CsrfToken csrfToken) {
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return Map.of(
                "registered", userAccountService.hasUser(),
                "authenticated", authenticated,
                "username", authenticated ? authentication.getName() : "",
                "csrfToken", csrfToken.getToken(),
                "csrfHeaderName", csrfToken.getHeaderName()
        );
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegistrationRequest request) {
        try {
            userAccountService.register(request.username(), request.password());
            return ResponseEntity.status(201).body(Map.of(
                    "registered", true,
                    "message", "注册成功，请登录"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
        }
    }

    public record RegistrationRequest(String username, String password) { }
}
