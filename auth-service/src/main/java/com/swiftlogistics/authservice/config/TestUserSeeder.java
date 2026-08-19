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
 * Creates the prototype's test accounts on startup.
 *
 * Runs every time the service boots but only inserts when an account is
 * missing, so restarting the container never fails on a duplicate username
 * and never quietly resets a password that was changed.
 *
 * A real system would have a registration flow instead. These exist so there
 * is something to log in with on a fresh database, covering both the CLIENT
 * and DRIVER roles.
 */
@Component
public class TestUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestUserSeeder.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Client account
    private final String clientUsername;
    private final String clientPassword;
    private final String clientRole;

    // Driver account
    private final String driverUsername;
    private final String driverPassword;
    private final String driverRole;

    public TestUserSeeder(AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${auth.seed-user.username}") String clientUsername,
                          @Value("${auth.seed-user.password}") String clientPassword,
                          @Value("${auth.seed-user.role}") String clientRole,
                          @Value("${auth.seed-driver.username}") String driverUsername,
                          @Value("${auth.seed-driver.password}") String driverPassword,
                          @Value("${auth.seed-driver.role}") String driverRole) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clientUsername = clientUsername;
        this.clientPassword = clientPassword;
        this.clientRole = clientRole;
        this.driverUsername = driverUsername;
        this.driverPassword = driverPassword;
        this.driverRole = driverRole;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUser(clientUsername, clientPassword, clientRole);
        seedUser(driverUsername, driverPassword, driverRole);
    }

    private void seedUser(String username, String rawPassword, String role) {
        if (userRepository.existsByUsername(username)) {
            log.info("User '{}' already exists, leaving it alone", username);
            return;
        }
        userRepository.save(AppUser.create(username, passwordEncoder.encode(rawPassword), role));
        log.info("Seeded user '{}' with role {}", username, role);
    }
}
