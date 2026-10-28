package com.ticketing.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record SignupRequest(@Email @NotBlank String email,
                                @NotBlank @Size(min = 8, max = 64) String password,
                                @NotBlank String name) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record TokenResponse(String accessToken) {}

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Long signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request.email(), request.password(), request.name());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return new TokenResponse(authService.login(request.email(), request.password()));
    }
}
