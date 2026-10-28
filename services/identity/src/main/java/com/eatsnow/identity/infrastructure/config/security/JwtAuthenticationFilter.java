package com.eatsnow.identity.infrastructure.config.security;

import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.infrastructure.adapter.out.crypto.RsaTokenIssuer;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validates incoming JWT access tokens or forwarded gateway identity headers (Spec 3.1 & ADR 005).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final RsaTokenIssuer tokenIssuer;

    public JwtAuthenticationFilter(RsaTokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            JWTClaimsSet claims = tokenIssuer.validateAndParse(token);
            if (claims != null) {
                try {
                    UUID userId = UUID.fromString(claims.getSubject());
                    List<String> roleStrings = claims.getStringListClaim("roles");
                    Set<Role> roles = roleStrings != null
                            ? roleStrings.stream().map(Role::valueOf).collect(Collectors.toSet())
                            : Collections.emptySet();

                    String email = claims.getStringClaim("email");
                    String restaurantIdStr = claims.getStringClaim("restaurantId");
                    UUID restaurantId = restaurantIdStr != null ? UUID.fromString(restaurantIdStr) : null;

                    String driverIdStr = claims.getStringClaim("driverId");
                    UUID driverId = driverIdStr != null ? UUID.fromString(driverIdStr) : null;

                    AuthenticatedUser user = new AuthenticatedUser(userId, email, roles, restaurantId, driverId);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            user.getAuthorities()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception ignored) {
                }
            }
        } else {
            // Support gateway forwarded headers (X-User-Id, X-User-Roles)
            String xUserId = request.getHeader("X-User-Id");
            if (xUserId != null && !xUserId.isBlank()) {
                try {
                    UUID userId = UUID.fromString(xUserId.trim());
                    String xRoles = request.getHeader("X-User-Roles");
                    Set<Role> roles = new HashSet<>();
                    if (xRoles != null && !xRoles.isBlank()) {
                        roles = Arrays.stream(xRoles.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .map(Role::valueOf)
                                .collect(Collectors.toSet());
                    }

                    String email = request.getHeader("X-User-Email");
                    String xRestId = request.getHeader("X-Restaurant-Id");
                    UUID restaurantId = xRestId != null && !xRestId.isBlank() ? UUID.fromString(xRestId.trim()) : null;

                    String xDriverId = request.getHeader("X-Driver-Id");
                    UUID driverId = xDriverId != null && !xDriverId.isBlank() ? UUID.fromString(xDriverId.trim()) : null;

                    AuthenticatedUser user = new AuthenticatedUser(userId, email, roles, restaurantId, driverId);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            user.getAuthorities()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception ignored) {
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
