package com.washcare.crm.customer;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.washcare.crm.auth.JwtAuthenticationFilter.AuthenticatedUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;

    public CustomerController(CustomerRepository customerRepository, VehicleRepository vehicleRepository) {
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public List<CustomerResponse> listCustomers(@RequestParam(defaultValue = "") String q) {
        List<Customer> customers = q.isBlank()
                ? customerRepository.findAllByOrderByNameAsc()
                : customerRepository.search(q.trim());
        return customers.stream().map(this::toResponse).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public CustomerResponse createWalkInCustomer(@Valid @RequestBody CustomerRequest request) {
        rejectDuplicateContacts(request.phone(), request.email(), null);
        return toResponse(customerRepository.save(Customer.staffCreated(
                request.name().trim(),
                request.phone().trim(),
                request.email(),
                request.notes())));
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public CustomerResponse updateCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerRequest request) {
        Customer customer = requireCustomer(customerId);
        rejectDuplicateContacts(request.phone(), request.email(), customer.getId());
        if (customer.getUser() != null
                && !Objects.equals(customer.getEmail(), Customer.normalizeEmail(request.email()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A linked account email can only be changed through a verified account update.");
        }
        customer.update(request.name(), request.phone(), request.email(), request.notes());
        return toResponse(customerRepository.save(customer));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerResponse getMyProfile(Authentication authentication) {
        return toResponse(getMyCustomer(authentication));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerResponse updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody CustomerRequest request) {
        Customer customer = getMyCustomer(authentication);
        rejectDuplicateContacts(request.phone(), null, customer.getId());
        customer.update(request.name(), request.phone(), customer.getEmail(), request.notes());
        return toResponse(customerRepository.save(customer));
    }

    @GetMapping("/me/vehicles")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<VehicleResponse> getMyVehicles(Authentication authentication) {
        Customer customer = getMyCustomer(authentication);
        return vehicleRepository.findByCustomerIdOrderByRegistrationNoAsc(customer.getId())
                .stream().map(this::toResponse).toList();
    }

    @PostMapping("/me/vehicles")
    @PreAuthorize("hasRole('CUSTOMER')")
    public VehicleResponse addMyVehicle(Authentication authentication, @Valid @RequestBody VehicleRequest request) {
        Customer customer = getMyCustomer(authentication);
        rejectDuplicateRegistration(request.registrationNo());
        return toResponse(vehicleRepository.save(new Vehicle(customer, request.registrationNo(), request.type())));
    }

    @GetMapping("/{customerId}/vehicles")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public List<VehicleResponse> getCustomerVehicles(@PathVariable Long customerId) {
        requireCustomer(customerId);
        return vehicleRepository.findByCustomerIdOrderByRegistrationNoAsc(customerId)
                .stream().map(this::toResponse).toList();
    }

    @PostMapping("/{customerId}/vehicles")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public VehicleResponse addCustomerVehicle(
            @PathVariable Long customerId,
            @Valid @RequestBody VehicleRequest request) {
        Customer customer = requireCustomer(customerId);
        rejectDuplicateRegistration(request.registrationNo());
        return toResponse(vehicleRepository.save(new Vehicle(customer, request.registrationNo(), request.type())));
    }

    private Customer getMyCustomer(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return customerRepository.findByUserId(user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found."));
    }

    private Customer requireCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found."));
    }

    private void rejectDuplicateContacts(String phone, String email, Long excludedCustomerId) {
        String normalizedPhone = Customer.normalizePhone(phone);
        boolean duplicate = excludedCustomerId == null
                ? customerRepository.existsByPhone(normalizedPhone)
                : customerRepository.existsByPhoneAndIdNot(normalizedPhone, excludedCustomerId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A customer with this phone number already exists.");
        }
        boolean duplicateEmail = email == null || email.isBlank()
                ? false
                : excludedCustomerId == null
                        ? customerRepository.existsByEmailIgnoreCase(email.trim())
                        : customerRepository.existsByEmailIgnoreCaseAndIdNot(email.trim(), excludedCustomerId);
        if (duplicateEmail) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A customer with this email already exists.");
        }
    }

    private void rejectDuplicateRegistration(String registrationNo) {
        if (vehicleRepository.existsByRegistrationNo(registrationNo.trim().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This vehicle is already registered.");
        }
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getNotes(),
                customer.getRegistrationSource().name(),
                customer.isActive());
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getCustomer().getId(),
                vehicle.getRegistrationNo(),
                vehicle.getType().name());
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Pattern(regexp = "[+0-9() .-]{7,32}") String phone,
            @Email @Size(max = 255) String email,
            @Size(max = 1000) String notes) {
    }

    public record VehicleRequest(
            @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9 -]+") String registrationNo,
            @NotNull Vehicle.VehicleType type) {
    }

    public record CustomerResponse(
            Long id,
            String name,
            String phone,
            String email,
            String notes,
            String registrationSource,
            boolean active) {
    }

    public record VehicleResponse(Long id, Long customerId, String registrationNo, String type) {
    }
}
