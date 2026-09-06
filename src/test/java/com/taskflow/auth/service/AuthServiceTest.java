package com.taskflow.auth.service;

import com.taskflow.auth.dto.LoginRequest;
import com.taskflow.auth.dto.LoginResponse;
import com.taskflow.auth.dto.RegisterRequest;
import com.taskflow.auth.dto.RegisterResponse;
import com.taskflow.auth.exception.DuplicateEmailException;
import com.taskflow.auth.exception.InvalidCredentialsException;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest("Jane Worker", "jane@example.com", "SecurePassword123!");
    }

    @Test
    @DisplayName("Successful registration: stores BCrypt hash, assigns Role.WORKER, and returns safe response")
    void shouldRegisterUserSuccessfully() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePassword123!")).thenReturn("$2a$10$mockHashedPasswordValue123");

        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        User savedUser = new User("Jane Worker", "jane@example.com", "$2a$10$mockHashedPasswordValue123", Role.WORKER);
        savedUser.setId(userId);
        savedUser.setCreatedAt(now);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RegisterResponse response = authService.register(validRequest);

        assertNotNull(response);
        assertEquals(userId, response.getId());
        assertEquals("Jane Worker", response.getName());
        assertEquals("jane@example.com", response.getEmail());
        assertEquals(Role.WORKER, response.getRole());
        assertEquals(now, response.getCreatedAt());

        // Verify entity captured at save
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User captured = userCaptor.getValue();

        assertEquals("Jane Worker", captured.getName());
        assertEquals("jane@example.com", captured.getEmail());
        assertEquals("$2a$10$mockHashedPasswordValue123", captured.getPasswordHash());
        assertNotEquals("SecurePassword123!", captured.getPasswordHash());
        assertEquals(Role.WORKER, captured.getRole());
    }

    @Test
    @DisplayName("Duplicate email is rejected with DuplicateEmailException without saving or hashing")
    void shouldRejectDuplicateEmail() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        DuplicateEmailException exception = assertThrows(
                DuplicateEmailException.class,
                () -> authService.register(validRequest)
        );

        assertEquals("Email is already registered: jane@example.com", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("Email is normalized (trimmed and lowercased) before duplicate check and persistence")
    void shouldNormalizeEmail() {
        RegisterRequest untrimmedRequest = new RegisterRequest("  Bob Builder  ", "   BOB@Example.COM  ", "SecurePassword123!");

        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePassword123!")).thenReturn("$2a$10$hashed");

        User savedUser = new User("Bob Builder", "bob@example.com", "$2a$10$hashed", Role.WORKER);
        savedUser.setId(UUID.randomUUID());
        savedUser.setCreatedAt(Instant.now());

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RegisterResponse response = authService.register(untrimmedRequest);

        assertEquals("bob@example.com", response.getEmail());
        assertEquals("Bob Builder", response.getName());
        verify(userRepository).existsByEmail("bob@example.com");
    }

    @Test
    @DisplayName("Successful login: finds user by email, verifies password via PasswordEncoder, returns safe LoginResponse")
    void shouldLoginSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = new User("Jane Worker", "jane@example.com", "$2a$10$hashedPassword", Role.WORKER);
        user.setId(userId);

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SecurePassword123!", "$2a$10$hashedPassword")).thenReturn(true);

        LoginRequest loginRequest = new LoginRequest("jane@example.com", "SecurePassword123!");
        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals(userId, response.getId());
        assertEquals("Jane Worker", response.getName());
        assertEquals("jane@example.com", response.getEmail());
        assertEquals(Role.WORKER, response.getRole());
    }

    @Test
    @DisplayName("Wrong password throws generic InvalidCredentialsException")
    void shouldFailLoginWhenPasswordIsWrong() {
        User user = new User("Jane Worker", "jane@example.com", "$2a$10$hashedPassword", Role.WORKER);

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword!", "$2a$10$hashedPassword")).thenReturn(false);

        LoginRequest loginRequest = new LoginRequest("jane@example.com", "WrongPassword!");
        InvalidCredentialsException ex = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    @DisplayName("Unknown email throws generic InvalidCredentialsException without revealing email absence")
    void shouldFailLoginWhenEmailNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        LoginRequest loginRequest = new LoginRequest("unknown@example.com", "SomePassword123!");
        InvalidCredentialsException ex = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals("Invalid email or password", ex.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Login normalizes email (trim and lowercase) before lookup")
    void shouldNormalizeEmailDuringLogin() {
        User user = new User("Jane Worker", "jane@example.com", "$2a$10$hashedPassword", Role.WORKER);

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SecurePassword123!", "$2a$10$hashedPassword")).thenReturn(true);

        LoginRequest loginRequest = new LoginRequest("   JANE@EXAMPLE.COM   ", "SecurePassword123!");
        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("jane@example.com", response.getEmail());
        verify(userRepository).findByEmail("jane@example.com");
    }
}

