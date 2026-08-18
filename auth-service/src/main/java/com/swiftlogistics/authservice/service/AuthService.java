package com.swiftlogistics.authservice.service;

import com.swiftlogistics.authservice.domain.AppUser;
import com.swiftlogistics.authservice.repository.AppUserRepository;
import com.swiftlogistics.authservice.security.JwtIssuer;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Checks credentials and hands back a token. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public AuthService(AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtIssuer jwtIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    /**
     * Verifies a username and password, returning the signed token.
     *
     * An unknown username and a wrong password both raise the same exception
     * with the same wording. Saying "no such user" would let anyone probe the
     * service to discover which accounts exist, which is a free head start for
     * whoever tries the passwords next.
     *
     * @throws InvalidCredentialsException if the credentials do not check out
     */
    public AuthenticatedUser login(String username, String rawPassword) {
        Optional<AppUser> found = userRepository.findByUsername(username);

        if (found.isEmpty() || !passwordEncoder.matches(rawPassword, found.get().getPasswordHash())) {
            log.warn("Failed login attempt for username '{}'", username);
            throw new InvalidCredentialsException("Invalid username or password");
        }

        AppUser user = found.get();
        log.info("User '{}' logged in", user.getUsername());

        return new AuthenticatedUser(
                jwtIssuer.issueFor(user),
                user.getUsername(),
                user.getRole(),
                jwtIssuer.expirySeconds());
    }

    /** A successful login: the token plus the few facts worth showing the client. */
    public record AuthenticatedUser(String token, String username, String role, long expiresInSeconds) {
    }
}
