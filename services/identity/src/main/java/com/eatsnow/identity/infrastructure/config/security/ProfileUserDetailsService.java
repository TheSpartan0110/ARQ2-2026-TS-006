package com.eatsnow.identity.infrastructure.config.security;

import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Login on the Identity pages uses the profile email and password. Inactive or not-yet-activated profiles cannot log
 * in (Spec 3.2). Returns Spring Security's own User so stored authorizations can be serialized safely.
 */
@Component
public class ProfileUserDetailsService implements UserDetailsService {

    private final ProfileRepositoryPort profiles;

    public ProfileUserDetailsService(ProfileRepositoryPort profiles) {
        this.profiles = profiles;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        Profile profile = profiles.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return User.withUsername(profile.email())
                .password(profile.isActivated() ? profile.passwordHash() : "")
                .disabled(!profile.canLogIn())
                .roles(profile.roles().stream().map(Enum::name).toArray(String[]::new))
                .build();
    }
}
