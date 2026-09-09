package com.taskflow.user.service;

import com.taskflow.auth.dto.UserResponse;
import com.taskflow.auth.exception.DuplicateEmailException;
import com.taskflow.user.dto.ChangePasswordRequest;
import com.taskflow.user.dto.CreateUserRequest;
import com.taskflow.user.dto.UpdateProfileRequest;
import com.taskflow.user.dto.UpdateRoleRequest;
import com.taskflow.user.dto.UpdateUserRequest;
import com.taskflow.user.exception.InvalidCurrentPasswordException;
import com.taskflow.user.exception.ProtectedUserOperationException;
import com.taskflow.user.exception.UserNotFoundException;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return toResponse(userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id)));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findCurrentUser(String authenticatedEmail) {
        return toResponse(findEntityByEmail(authenticatedEmail));
    }

    @Transactional
    public UserResponse updateCurrentUser(String authenticatedEmail, UpdateProfileRequest request) {
        User user = findEntityByEmail(authenticatedEmail);
        applyProfileUpdate(user, request.getName(), request.getEmail());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.getEmail());
        ensureEmailAvailable(email);
        User user = new User(request.getName().trim(), email,
                passwordEncoder.encode(request.getPassword()), request.getRole());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findEntityById(id);
        applyProfileUpdate(user, request.getName(), request.getEmail());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse changeRole(String authenticatedEmail, UUID id, UpdateRoleRequest request) {
        User actor = findEntityByEmail(authenticatedEmail);
        User target = findEntityById(id);

        if (actor.getId().equals(target.getId())) {
            throw new ProtectedUserOperationException("Administrators cannot change their own role");
        }
        if (target.getRole() == Role.ADMIN && request.getRole() != Role.ADMIN && countAdmins() <= 1) {
            throw new ProtectedUserOperationException("The last administrator cannot be demoted");
        }

        target.setRole(request.getRole());
        return toResponse(userRepository.save(target));
    }

    @Transactional
    public UserResponse changePassword(String authenticatedEmail, ChangePasswordRequest request) {
        User user = findEntityByEmail(authenticatedEmail);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException("Current password is invalid");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(String authenticatedEmail, UUID id) {
        User actor = findEntityByEmail(authenticatedEmail);
        User target = findEntityById(id);

        if (actor.getId().equals(target.getId())) {
            throw new ProtectedUserOperationException("Administrators cannot delete themselves");
        }
        if (target.getRole() == Role.ADMIN && countAdmins() <= 1) {
            throw new ProtectedUserOperationException("The last administrator cannot be deleted");
        }
        userRepository.delete(target);
    }

    private User findEntityById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }

    private User findEntityByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + normalizedEmail));
    }

    private void applyProfileUpdate(User user, String name, String email) {
        if (name != null) {
            String normalizedName = name.trim();
            if (normalizedName.isEmpty()) {
                throw new IllegalArgumentException("Name must not be blank");
            }
            user.setName(normalizedName);
        }

        if (email != null) {
            String normalizedEmail = normalizeEmail(email);
            if (!normalizedEmail.equals(user.getEmail())) {
                ensureEmailAvailable(normalizedEmail, user.getId());
            }
            user.setEmail(normalizedEmail);
        }
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email is already registered: " + email);
        }
    }

    private void ensureEmailAvailable(String email, UUID currentUserId) {
        if (userRepository.existsByEmailAndIdNot(email, currentUserId)) {
            throw new DuplicateEmailException("Email is already registered: " + email);
        }
    }

    private long countAdmins() {
        return userRepository.findByRole(Role.ADMIN).size();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.from(user);
    }
}