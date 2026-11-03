package com.eatsnow.security;

import com.eatsnow.common.web.dto.ErrorResponseDto;
import com.eatsnow.common.web.filter.CorrelationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates every request (Spec 3.1: "Gateway and services verify..."): 401 UNAUTHENTICATED without a valid bearer
 * token and 403 FORBIDDEN when the caller lacks the role this service requires. The management port (Actuator) is a
 * separate server and never passes through here. Ownership of each object is checked by the use cases.
 */
public class BearerAuthenticationFilter extends OncePerRequestFilter {
    private static final String ATTRIBUTE = BearerAuthenticationFilter.class.getName() + ".user";

    private final IdentityTokenVerifier verifier;
    private final ObjectMapper mapper;
    private final String requiredRole;

    public BearerAuthenticationFilter(IdentityTokenVerifier verifier, ObjectMapper mapper, String requiredRole) {
        this.verifier = verifier;
        this.mapper = mapper;
        this.requiredRole = requiredRole;
    }

    /** The authenticated caller of this request; only valid for requests that passed this filter. */
    public static AuthenticatedUser currentUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(ATTRIBUTE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response, 401, "UNAUTHENTICATED", "A valid bearer access token is required");
            return;
        }
        Optional<AuthenticatedUser> user = verifier.verify(header.substring(7).trim());
        if (user.isEmpty()) {
            reject(response, 401, "UNAUTHENTICATED", "The access token is invalid or expired");
            return;
        }
        if (!user.get().roles().contains(requiredRole)) {
            reject(response, 403, "FORBIDDEN", "This endpoint requires the " + requiredRole + " role");
            return;
        }
        request.setAttribute(ATTRIBUTE, user.get());
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(),
                new ErrorResponseDto(code, message, CorrelationContext.getCurrentCorrelationId(), null));
    }
}
