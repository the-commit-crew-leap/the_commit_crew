package com.thecommitcrew.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;

class CustomUserDetailsServiceTest {

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new CustomUserDetailsService();
    }

    @Test
    void loadUserByUsername_shouldReturnUserWithProvidedUsername() {
        String username = "testuser";
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(username);
    }

    @Test
    void loadUserByUsername_shouldReturnUserWithRoleUser() {
        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");
        
        assertThat(userDetails.getAuthorities()).isNotEmpty();
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .contains("ROLE_USER");
    }

    @Test
    void loadUserByUsername_shouldReturnUserWithEmptyPassword() {
        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");
        
        assertThat(userDetails.getPassword()).isEmpty();
    }

    @Test
    void loadUserByUsername_shouldBeEnabledByDefault() {
        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");
        
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsername_withDifferentUsernames() {
        String[] usernames = {"alice", "bob", "charlie"};
        
        for (String username : usernames) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            assertThat(userDetails.getUsername()).isEqualTo(username);
        }
    }
}