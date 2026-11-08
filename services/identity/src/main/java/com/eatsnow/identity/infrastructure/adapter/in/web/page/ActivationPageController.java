package com.eatsnow.identity.infrastructure.adapter.in.web.page;

import com.eatsnow.common.domain.exception.DomainException;
import com.eatsnow.identity.domain.port.in.ActivateAccountUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Activation page for accounts created by an ADMIN (Spec 3.2): the emailed one-time link opens it and the person sets
 * their password.
 */
@Controller
public class ActivationPageController {

    private final ActivateAccountUseCase activateAccountUseCase;

    public ActivationPageController(ActivateAccountUseCase activateAccountUseCase) {
        this.activateAccountUseCase = activateAccountUseCase;
    }

    @GetMapping("/activate")
    public String form(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("usable", activateAccountUseCase.isUsable(token));
        return "activate";
    }

    @PostMapping("/activate")
    public String activate(@RequestParam String token, @RequestParam String password,
                           @RequestParam String confirmPassword, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("usable", true);
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "The passwords do not match");
            return "activate";
        }
        try {
            activateAccountUseCase.activate(token, password);
        } catch (DomainException ex) {
            model.addAttribute("error", ex.getMessage());
            return "activate";
        }
        return "redirect:/login?activated";
    }
}
