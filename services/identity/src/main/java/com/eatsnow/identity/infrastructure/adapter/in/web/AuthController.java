package com.eatsnow.identity.infrastructure.adapter.in.web;

import com.eatsnow.identity.domain.model.IssuedToken;
import com.eatsnow.identity.domain.port.in.AuthenticateUseCase;
import com.eatsnow.identity.domain.port.out.TokenIssuerPort;
import com.eatsnow.identity.infrastructure.adapter.in.web.dto.AuthRequestDto;
import com.eatsnow.identity.infrastructure.adapter.in.web.dto.AuthResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing authentication and public JWKS endpoint (Spec 3.1 & ADR 005).
 */
@RestController
public class AuthController {

    private final AuthenticateUseCase authenticateUseCase;
    private final TokenIssuerPort tokenIssuerPort;

    public AuthController(AuthenticateUseCase authenticateUseCase, TokenIssuerPort tokenIssuerPort) {
        this.authenticateUseCase = authenticateUseCase;
        this.tokenIssuerPort = tokenIssuerPort;
    }

    @PostMapping("/api/v1/auth/token")
    public ResponseEntity<AuthResponseDto> createToken(@Valid @RequestBody AuthRequestDto request) {
        IssuedToken token = authenticateUseCase.authenticate(request.email(), request.password());
        return ResponseEntity.ok(AuthResponseDto.fromDomain(token));
    }

    @GetMapping(value = {"/.well-known/jwks.json", "/oauth2/jwks"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getJwks() {
        return ResponseEntity.ok(tokenIssuerPort.getPublicJwksJson());
    }
}
