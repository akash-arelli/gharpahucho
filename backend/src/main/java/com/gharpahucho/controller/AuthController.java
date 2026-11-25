package com.gharpahucho.controller;

import com.gharpahucho.dto.UserDtos.AuthResponse;
import com.gharpahucho.dto.UserDtos.LoginRequest;
import com.gharpahucho.dto.UserDtos.RegisterRequest;
import com.gharpahucho.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService users;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return users.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return users.login(request);
    }

    /** One-click sign-in as the seeded demo traveller; 403 unless demo mode is on. */
    @PostMapping("/demo-login")
    public AuthResponse demoLogin() {
        return users.demoLogin();
    }
}
