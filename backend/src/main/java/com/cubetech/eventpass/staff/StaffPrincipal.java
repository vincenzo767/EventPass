package com.cubetech.eventpass.staff;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The authenticated staff member stored in the HTTP session (the password hash is erased after login). */
public final class StaffPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String name;
    private final String username;
    private final StaffRole role;
    private final boolean active;
    private String passwordHash;

    public StaffPrincipal(StaffUser user) {
        this.id = user.getId();
        this.name = user.getName();
        this.username = user.getUsername();
        this.role = user.getRole();
        this.active = user.isActive();
        this.passwordHash = user.getPasswordHash();
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public StaffRole role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    /** Called by Spring Security after authentication so the hash is never kept in the session. */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
