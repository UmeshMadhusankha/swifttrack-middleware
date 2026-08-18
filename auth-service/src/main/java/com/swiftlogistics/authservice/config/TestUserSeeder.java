package com.swiftlogistics.authservice.config;

import com.swiftlogistics.authservice.domain.AppUser;
import com.swiftlogistics.authservice.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the prototype's single test account on startup.
 *
 * Runs every time the service boots but only inserts when the account is
 * missing, so restarting the container never fails on a duplicate username and
 * never quietly resets a password that was changed.
 *
 * A real system would have a registration flow instead. This exists so there is
 * something to log in with on a fresh database.
 */
@Component
public class TestUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestUserSeeder.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String role;

    public TestUserSeeder(AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${auth.seed-user.username}") String username,
                          @Value("${auth.seed-user.password}") String password,
                          @Value("${auth.seed-user.role}") String role) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(username)) {
            log.info("Test user '{}' already exists, leaving it alone", username);
            return;
        }

        userRepository.save(AppUser.create(username, passwordEncoder.encode(password), role));
        log.info("Seeded test user '{}' with role {}", username, role);
    }
}
