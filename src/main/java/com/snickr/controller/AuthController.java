package com.snickr.controller;

import com.snickr.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A controller responsible for handling user registration,
 * login page routing, and homepage redirection.
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Index page
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * Login page
     */
    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }

    /**
     * Registration page
     */
    @GetMapping("/register")
    public String showRegistrationForm() {
        return "register";
    }

    /**
     * Handling registration form
     */
    @PostMapping("/register")
    public String processRegistration(
            @RequestParam("email") String email,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            userService.registerUser(email, username, password);
            redirectAttributes.addAttribute("success", "true");
            return "redirect:/login";

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    /**
     * Temporary Dashboard Route
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }
}