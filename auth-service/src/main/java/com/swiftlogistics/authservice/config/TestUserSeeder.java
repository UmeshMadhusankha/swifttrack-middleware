package com.swiftlogistics.authservice.config;

import com.swiftlogistics.authservice.domain.AppUser;
import com.swiftlogistics.authservice.repository.AppUserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the prototype's three demo accounts on startup: one per role.
 *
 * Runs every time the service boots. Unlike a "create if missing" seeder this
 * one re-applies the password and role every time, because the demo depends on
 * these exact credentials working on a database that may already hold an older
 * version of the same account. A real system would have a registration flow
 * and nothing like this class.
 *
 * Accounts from earlier versions of the prototype are removed so that exactly
 * these three logins exist.
 */
@Component
public class TestUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestUserSeeder.class);

    /** The demo password is shared by all three accounts on purpose: it is a prototype. */
    private static final String DEMO_PASSWORD = "swift2026";

    private static final List<SeedAccount> ACCOUNTS = List.of(
            new SeedAccount("admin", DEMO_PASSWORD, "ADMIN"),
            new SeedAccount("client", DEMO_PASSWORD, "CLIENT"),
            new SeedAccount("driver", DEMO_PASSWORD, "DRIVER"));

    /** Seed accounts from earlier builds, deleted so only the three above remain. */
    private static final List<String> RETIRED_USERNAMES = List.of("acme-corp", "driver-01");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestUserSeeder(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        ACCOUNTS.forEach(this::seed);
        RETIRED_USERNAMES.forEach(this::retire);
    }

    private void seed(SeedAccount account) {
        userRepository.findByUsername(account.username())
                .ifPresentOrElse(
                        existing -> {
                            userRepository.delete(existing);
                            userRepository.flush();
                            insert(account);
                            log.info("Re-seeded user '{}' with role {}", account.username(), account.role());
                        },
                        () -> {
                            insert(account);
                            log.info("Seeded user '{}' with role {}", account.username(), account.role());
                        });
    }

    private void insert(SeedAccount account) {
        userRepository.save(AppUser.create(
                account.username(),
                passwordEncoder.encode(account.password()),
                account.role()));
    }

    private void retire(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            userRepository.delete(user);
            log.info("Removed retired prototype account '{}'", username);
        });
    }

    private record SeedAccount(String username, String password, String role) {
    }
}
