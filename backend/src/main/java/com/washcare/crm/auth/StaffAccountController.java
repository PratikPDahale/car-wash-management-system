package com.washcare.crm.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.washcare.crm.notification.NotificationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffAccountController {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public StaffAccountController(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public AuthenticationController.UserResponse createStaffAccount(@Valid @RequestBody CreateStaffRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        UserAccount staff = userRepository.save(new UserAccount(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                UserRole.STAFF));
        notificationService.welcome(staff);
        return new AuthenticationController.UserResponse(staff.getId(), staff.getFullName(), staff.getEmail(), staff.getRole().name());
    }

    public record CreateStaffRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 10, max = 72) String password) {
    }
}
