package com.washcare.crm.search;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.washcare.crm.auth.JwtAuthenticationFilter.AuthenticatedUser;
import com.washcare.crm.booking.Booking;
import com.washcare.crm.booking.BookingRepository;
import com.washcare.crm.customer.Customer;
import com.washcare.crm.customer.CustomerRepository;
import com.washcare.crm.customer.Vehicle;
import com.washcare.crm.customer.VehicleRepository;
import com.washcare.crm.service.ServiceOffering;
import com.washcare.crm.service.ServiceOfferingRepository;
import com.washcare.crm.search.SearchController.BookingResult;
import com.washcare.crm.search.SearchController.CustomerResult;
import com.washcare.crm.search.SearchController.SearchResponse;
import com.washcare.crm.search.SearchController.ServiceResult;
import com.washcare.crm.search.SearchController.VehicleResult;

@Service
public class SearchService {

    private static final int RESULTS_PER_CATEGORY = 5;

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;

    public SearchService(
            CustomerRepository customerRepository,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository,
            ServiceOfferingRepository serviceOfferingRepository) {
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String query, AuthenticatedUser user, boolean customer, boolean owner) {
        PageRequest firstFive = PageRequest.of(0, RESULTS_PER_CATEGORY);
        List<CustomerResult> customers = customer
                ? List.of()
                : customerRepository.searchLimited(query, firstFive).stream()
                        .map(this::toCustomerResult)
                        .toList();
        List<VehicleResult> vehicles = (customer
                ? vehicleRepository.searchForCustomer(query, user.id(), firstFive)
                : vehicleRepository.searchForShop(query, firstFive))
                .stream()
                .map(this::toVehicleResult)
                .toList();

        Long bookingId = parseBookingId(query);
        List<BookingResult> bookings = (customer
                ? bookingRepository.searchForCustomer(query, bookingId, user.id(), firstFive)
                : bookingRepository.searchForShop(query, bookingId, firstFive))
                .stream()
                .map(this::toBookingResult)
                .toList();

        List<ServiceOffering> services = serviceOfferingRepository.searchByName(
                query, !owner, firstFive);
        return new SearchResponse(
                customers,
                vehicles,
                bookings,
                services.stream().map(this::toServiceResult).toList());
    }

    private static Long parseBookingId(String query) {
        if (!query.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.valueOf(query);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private CustomerResult toCustomerResult(Customer customer) {
        return new CustomerResult(customer.getId(), customer.getName(), customer.getPhone(), customer.getEmail());
    }

    private BookingResult toBookingResult(Booking booking) {
        return new BookingResult(
                booking.getId(),
                booking.getCustomer().getName(),
                booking.getVehicle().getRegistrationNo(),
                booking.getService().getName(),
                booking.getBookingDate(),
                booking.getStartTime(),
                booking.getStatus().name());
    }

    private VehicleResult toVehicleResult(Vehicle vehicle) {
        return new VehicleResult(
                vehicle.getId(),
                vehicle.getCustomer().getName(),
                vehicle.getRegistrationNo(),
                vehicle.getType().name());
    }

    private ServiceResult toServiceResult(ServiceOffering service) {
        return new ServiceResult(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.isActive());
    }
}
