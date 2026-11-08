package com.eatsnow.identity.infrastructure.adapter.in.web.page;

import com.eatsnow.common.domain.exception.DomainException;
import com.eatsnow.identity.domain.port.in.RegisterCustomerUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Customer self-registration page (Spec 3.1 & ADR 005). New accounts always get the USER role.
 */
@Controller
public class RegistrationPageController {

    private final RegisterCustomerUseCase registerCustomerUseCase;

    public RegistrationPageController(RegisterCustomerUseCase registerCustomerUseCase) {
        this.registerCustomerUseCase = registerCustomerUseCase;
    }

    @GetMapping("/register")
    public String form() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String displayName, @RequestParam String email,
                           @RequestParam String password, @RequestParam String confirmPassword, Model model) {
        model.addAttribute("displayName", displayName);
        model.addAttribute("email", email);
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "The passwords do not match");
            return "register";
        }
        try {
            registerCustomerUseCase.register(displayName, email, password);
        } catch (DomainException ex) {                 // invalid input or email already registered
            model.addAttribute("error", ex.getMessage());
            return "register";
        }
        return "redirect:/login?registered";
    }
}
