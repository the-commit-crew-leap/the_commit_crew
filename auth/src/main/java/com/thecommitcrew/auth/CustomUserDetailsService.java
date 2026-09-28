package com.thecommitcrew.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Stub UserDetailsService - loads user details for authenticated tokens
 * This is a placeholder until real user database queries are implemented
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // TODO: Replace with real database query to get user
        // For now, return a stub user with the username from the token
        
        return new User(
                username,
                "",  // Empty password (not used for JWT)
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}