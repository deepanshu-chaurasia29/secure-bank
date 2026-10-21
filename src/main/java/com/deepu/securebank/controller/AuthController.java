package com.deepu.securebank.controller;

import com.deepu.securebank.dto.*;
import com.deepu.securebank.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1. Authentication", description = "Register, log in, change password")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new customer and create the account", security = {})
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Log in and get a JWT token (locks after 3 wrong passwords)", security = {})
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // The user id comes from the verified JWT, never from a client-supplied id.
    @Operation(summary = "Get the authenticated user's profile")
    @GetMapping("/me")
    public CustomerProfileResponse myProfile(@AuthenticationPrincipal Long userId) {
        return authService.getMyProfile(userId);
    }

    // Needs a valid JWT (it is not in the public list in SecurityConfig).
    @Operation(summary = "Change your own password")
    @PostMapping("/change-password")
    public MessageResponse changePassword(@AuthenticationPrincipal Long userId,
                                          @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return new MessageResponse("Password changed successfully");
    }
}
