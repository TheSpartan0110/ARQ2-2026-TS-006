package com.eatsnow.identity.infrastructure.adapter.out.persistence;

import com.eatsnow.identity.domain.port.out.TokenRevocationPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Deletes every Spring Authorization Server authorization of a user, which invalidates their refresh tokens at once.
 * Authorizations are keyed by the login name, which is the profile email.
 */
@Component
public class JdbcTokenRevocationAdapter implements TokenRevocationPort {

    private final JdbcTemplate jdbc;

    public JdbcTokenRevocationAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void revokeAll(String email) {
        jdbc.update("DELETE FROM identity.oauth2_authorization WHERE principal_name = ?", email);
    }
}
