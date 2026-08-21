package com.havenbank.backend.iam.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.havenbank.backend.iam.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Adapts a domain {@link User} to Spring Security's {@link UserDetails}. Roles are exposed as
 * {@code ROLE_*} authorities and permissions as bare authorities, so both {@code hasRole(..)} and
 * {@code hasAuthority(..)} checks work. Consumed by the authorization-server module during login.
 */
@Getter
public class AppUserPrincipal implements UserDetails {

    private final UUID userId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppUserPrincipal(User user) {
        this.userId = user.getId();
        this.username = user.getEmail();
        this.password = user.getPasswordHash();
        this.enabled = user.isActive();
        this.authorities = user.getRoles().stream()
                .flatMap(role -> Stream.concat(
                        Stream.of(new SimpleGrantedAuthority("ROLE_" + role.getName())),
                        role.getPermissions().stream()
                                .map(p -> new SimpleGrantedAuthority(p.getName()))))
                .collect(Collectors.toSet());
    }

    /**
     * Used only by Jackson to rebuild this principal when {@code JdbcOAuth2AuthorizationService}
     * reads back a stored authorization (the persisted {@code Authentication}'s principal). Not for
     * application code &mdash; construct from a {@link User} instead. Required because this class has
     * no no-arg constructor/setters for Jackson to use by default.
     */
    @JsonCreator
    private AppUserPrincipal(@JsonProperty("userId") UUID userId, @JsonProperty("username") String username,
                             @JsonProperty("password") String password, @JsonProperty("enabled") boolean enabled,
                             @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}