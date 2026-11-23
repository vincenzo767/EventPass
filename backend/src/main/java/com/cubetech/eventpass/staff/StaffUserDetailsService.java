package com.cubetech.eventpass.staff;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class StaffUserDetailsService implements UserDetailsService {

    private final StaffUserRepository repository;

    public StaffUserDetailsService(StaffUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return repository.findByUsername(normalized)
                .map(StaffPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
