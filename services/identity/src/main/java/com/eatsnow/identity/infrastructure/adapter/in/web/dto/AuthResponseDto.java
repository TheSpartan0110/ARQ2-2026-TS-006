package com.eatsnow.identity.infrastructure.adapter.in.web.dto;

import com.eatsnow.identity.domain.model.IssuedToken;

public record AuthResponseDto(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static AuthResponseDto fromDomain(IssuedToken token) {
        return new AuthResponseDto(token.token(), token.tokenType(), token.expiresIn());
    }
}
