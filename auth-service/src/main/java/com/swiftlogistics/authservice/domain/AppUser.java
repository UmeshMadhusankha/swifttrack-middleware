package com.swiftlogistics.authservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Someone who can log in to the SwiftLogistics portal.
 *
 * The table is called app_user because USER is a reserved word in Postgres.
 *
 * Only the hash of the password is ever stored. Even in a prototype that is
 * worth doing properly: a plain-text password column is the kind of thing that
 * gets copied into a real system later and quietly becomes a breach.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    /** Coarse permission label, e.g. CLIENT or ADMIN. Carried in the token. */
    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private Instant createdAt;

    protected AppUser() {
        // Required by JPA.
    }

    private AppUser(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = Instant.now();
    }

    /** @param passwordHash already hashed; this class never sees a raw password */
    public static AppUser create(String username, String passwordHash, String role) {
        return new AppUser(username, passwordHash, role);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
