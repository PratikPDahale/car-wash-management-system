package com.washcare.crm.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import com.washcare.crm.customer.Customer;
import com.washcare.crm.customer.CustomerRepository;
import com.washcare.crm.notification.NotificationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final UserAccountRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NotificationService notificationService;

    public AuthenticationController(
            UserAccountRepository userRepository,
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.notificationService = notificationService;
    }

    @PostMapping("/register")
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        if (customerRepository.existsByPhone(Customer.normalizePhone(request.phone()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A customer profile with this phone number already exists. Ask the shop to verify and link your profile.");
        }
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A customer profile with this email already exists. Ask the shop to verify and link your profile.");
        }

        UserAccount user = userRepository.save(new UserAccount(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                UserRole.CUSTOMER));
        customerRepository.save(Customer.onlineRegistration(
                user,
                request.name().trim(),
                request.phone().trim(),
                email));
        notificationService.welcome(user);
        return responseFor(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = userRepository.findByEmail(UserAccount.normalizeEmail(request.email()))
                .filter(UserAccount::isActive)
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Email or password is incorrect."));
        return responseFor(user);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public UserResponse currentUser(Authentication authentication) {
        var authenticatedUser = (JwtAuthenticationFilter.AuthenticatedUser) authentication.getPrincipal();
        UserAccount user = userRepository.findById(authenticatedUser.id())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is no longer active."));
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole().name());
    }

    private AuthResponse responseFor(UserAccount user) {
        return new AuthResponse(
                jwtService.createToken(user),
                new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole().name()));
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "[+0-9() .-]{7,32}") String phone,
            @NotBlank @Size(min = 10, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, UserResponse user) {
    }

    public record UserResponse(Long id, String name, String email, String role) {
    }
}
