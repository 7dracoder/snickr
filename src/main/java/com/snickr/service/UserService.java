package com.snickr.service;

import com.snickr.model.User;
import com.snickr.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * User-related service processing layer
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Core logic for registering a new user.
     *
     * @param email       The user's email address.
     * @param username    The username.
     * @param rawPassword The raw password (plaintext).
     * @return The complete User object, including a UUID and timestamp, upon successful registration.
     * @throws IllegalArgumentException If the email or username is already taken.
     */
    public User registerUser(String email, String username, String rawPassword) {
        // Check if the email address has already been registered
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("This email address is already registered！");
        }

        // Check if the username is already taken
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("This username is already taken！");
        }

        // Hash Encryption
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Construct User object
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setUsername(username);
        newUser.setPasswordHash(encodedPassword);

        // Save to database
        return userRepository.save(newUser);
    }

    /**
     * Retrieve user information by username
     */
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}