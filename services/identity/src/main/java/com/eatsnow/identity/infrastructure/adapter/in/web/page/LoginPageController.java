package com.eatsnow.identity.infrastructure.adapter.in.web.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Login page of the OIDC provider (ADR 005). Spring Security processes POST /login; the authorization request that
 * sent the browser here resumes after a successful login.
 */
@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
