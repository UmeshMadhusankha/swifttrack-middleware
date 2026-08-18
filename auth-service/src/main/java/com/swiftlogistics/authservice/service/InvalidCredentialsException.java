package com.swiftlogistics.authservice.service;

/** Raised when a username does not exist, or the password does not match. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
