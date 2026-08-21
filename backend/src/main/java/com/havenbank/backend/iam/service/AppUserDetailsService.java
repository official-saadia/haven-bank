package com.havenbank.backend.iam.service;

import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.iam.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads users for authentication by email. Note the deliberately generic exception message: the
 * "unknown user" and "wrong password" paths must be indistinguishable to callers to prevent account
 * enumeration (FR-1.7). The authentication provider maps both to the same failure.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(username)
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }
}
