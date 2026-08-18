package com.swiftlogistics.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Supplies the password hasher.
 *
 * BCrypt is deliberately slow, which is the point: it makes guessing millions
 * of passwords against a stolen database impractical. It also salts each hash
 * automatically, so two users with the same password still get different hashes.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
