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
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    @Test
    void shouldCreateUserWithHashedPasswordAndSuppliedRole() {
        when(userRepository.existsByEmail("manager@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed");
        User saved = new User("Manager", "manager@example.com", "hashed", Role.MANAGER);
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserResponse response = userService.create(new CreateUserRequest(
                "Manager", " MANAGER@EXAMPLE.COM ", "Password123!", Role.MANAGER));

        assertEquals(Role.MANAGER, response.role());
        verify(passwordEncoder).encode("Password123!");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldUpdateOwnProfileWithoutChangingPasswordOrRole() {
        UUID id = UUID.randomUUID();
        User user = new User("Old", "old@example.com", "old-hash", Role.WORKER);
        user.setId(id);
        when(userRepository.findByEmail("old@example.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("new@example.com", id)).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        userService.updateCurrentUser("old@example.com", new UpdateProfileRequest(" New ", " NEW@EXAMPLE.COM "));

        assertEquals("New", user.getName());
        assertEquals("new@example.com", user.getEmail());
        assertEquals("old-hash", user.getPasswordHash());
        assertEquals(Role.WORKER, user.getRole());
        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    void shouldChangePasswordOnlyAfterCurrentPasswordVerification() {
        User user = new User("Worker", "worker@example.com", "old-hash", Role.WORKER);
        when(userRepository.findByEmail("worker@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(userRepository.save(user)).thenReturn(user);

        userService.changePassword("worker@example.com", new ChangePasswordRequest("old", "new-password"));

        assertEquals("new-hash", user.getPasswordHash());
        verify(passwordEncoder).matches("old", "old-hash");
    }

    @Test
    void shouldRejectWrongCurrentPassword() {
        User user = new User("Worker", "worker@example.com", "old-hash", Role.WORKER);
        when(userRepository.findByEmail("worker@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThrows(InvalidCurrentPasswordException.class, () -> userService.changePassword(
                "worker@example.com", new ChangePasswordRequest("wrong", "new-password")));
        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    void shouldProtectLastAdminAndSelfRoleChangesAndDeletion() {
        UUID id = UUID.randomUUID();
        User admin = new User("Admin", "admin@example.com", "hash", Role.ADMIN);
        admin.setId(id);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(userRepository.findById(id)).thenReturn(Optional.of(admin));
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        assertThrows(ProtectedUserOperationException.class, () -> userService.changeRole(
                "admin@example.com", id, new UpdateRoleRequest(Role.WORKER)));
        assertThrows(ProtectedUserOperationException.class, () -> userService.delete("admin@example.com", id));

        UUID otherId = UUID.randomUUID();
        User otherAdmin = new User("Other", "other@example.com", "hash", Role.ADMIN);
        otherAdmin.setId(otherId);
        when(userRepository.findById(otherId)).thenReturn(Optional.of(otherAdmin));
        assertThrows(ProtectedUserOperationException.class, () -> userService.changeRole(
                "admin@example.com", otherId, new UpdateRoleRequest(Role.WORKER)));
    }

    @Test
    void shouldRejectDuplicateEmailOnCreateAndUpdate() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);
        assertThrows(DuplicateEmailException.class, () -> userService.create(
                new CreateUserRequest("Name", "taken@example.com", "Password123!", Role.WORKER)));

        User user = new User("Name", "old@example.com", "hash", Role.WORKER);
        user.setId(UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("taken@example.com", user.getId())).thenReturn(true);
        assertThrows(DuplicateEmailException.class, () -> userService.update(
                user.getId(), new UpdateUserRequest(null, "taken@example.com")));
    }
}
