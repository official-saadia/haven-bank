package com.havenbank.backend.authserver.controller;

import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the login page for the authorization-code flow.
 */
@Controller
public class LoginController {

    /**
     * Registration is a SPA route on a different origin, so the login page cannot link to it with a
     * relative path. Same property the CSP {@code form-action} is built from, for one source of truth.
     */
    @Value("${app.spa.base-url:http://localhost:5173}")
    private String spaBaseUrl;

    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("registerUrl", spaBaseUrl + "/register");
        model.addAttribute("forgotUrl", spaBaseUrl + "/forgot-password");
        return "login";
    }
}