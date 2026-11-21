package com.washcare.crm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
		"washcare.owner.email=owner@example.com",
		"washcare.owner.password=owner-test-password-123"
})
@AutoConfigureMockMvc
class WashCareCrmApiApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void activeServiceCatalogIsPublicAndReturnsSeededServices() throws Exception {
		mockMvc.perform(get("/api/v1/services"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").isNotEmpty())
				.andExpect(jsonPath("$[0].active").value(true));
	}

	@Test
	void globalSearchFindsShopRecordsAndScopesCustomerResults() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String ownerToken = signInOwner();
		MvcResult serviceResult = mockMvc.perform(post("/api/v1/services")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Search Wash %s","description":"Search feature test service.","price":350.00,"estimatedDurationMinutes":30,"active":true}
						""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		long serviceId = objectMapper.readTree(serviceResult.getResponse().getContentAsString()).get("id").asLong();

		String customerToken = registerCustomer("search" + suffix);
		long vehicleId = addCustomerVehicle(customerToken, "SRCH-" + suffix);
		LocalDate appointmentDate = LocalDate.now().plusWeeks(1)
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
		MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"serviceId":%d,"vehicleId":%d,"date":"%s","startTime":"15:00"}
						""".formatted(serviceId, vehicleId, appointmentDate)))
				.andExpect(status().isOk())
				.andReturn();
		long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asLong();
		String otherCustomerToken = registerCustomer("searchother" + suffix);

		mockMvc.perform(get("/api/v1/search").param("q", suffix)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customers[0].name").value("Booking Customer search" + suffix))
				.andExpect(jsonPath("$.vehicles[0].registrationNo").value("SRCH-" + suffix.toUpperCase()))
				.andExpect(jsonPath("$.bookings[0].id").value(bookingId))
				.andExpect(jsonPath("$.services[0].id").value(serviceId));

		mockMvc.perform(get("/api/v1/search").param("q", suffix)
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customers.length()").value(0))
				.andExpect(jsonPath("$.vehicles[0].registrationNo").value("SRCH-" + suffix.toUpperCase()))
				.andExpect(jsonPath("$.bookings[0].id").value(bookingId))
				.andExpect(jsonPath("$.services[0].id").value(serviceId));

		mockMvc.perform(get("/api/v1/search").param("q", "SRCH-" + suffix)
				.header("Authorization", "Bearer " + otherCustomerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vehicles.length()").value(0))
				.andExpect(jsonPath("$.bookings.length()").value(0));

		mockMvc.perform(get("/api/v1/search").param("q", suffix))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void notificationCenterRoutesBookingAlertsAndSupportsPreferencesAndAnnouncements() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String ownerToken = signInOwner();
		String customerToken = registerCustomer("notice" + suffix);
		long vehicleId = addCustomerVehicle(customerToken, "NOTE-" + suffix);

		MvcResult welcomeInbox = mockMvc.perform(get("/api/v1/notifications")
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(1))
				.andExpect(jsonPath("$.items[0].category").value("SYSTEM"))
				.andReturn();
		long welcomeId = objectMapper.readTree(welcomeInbox.getResponse().getContentAsString())
				.get("items").get(0).get("id").asLong();

		mockMvc.perform(put("/api/v1/notifications/" + welcomeId + "/read")
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.readAt").isNotEmpty());
		mockMvc.perform(get("/api/v1/notifications")
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(0));

		MvcResult serviceResult = mockMvc.perform(post("/api/v1/services")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Notification Wash %s","description":"Notification test.","price":400.00,"estimatedDurationMinutes":30,"active":true}
						""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		long serviceId = objectMapper.readTree(serviceResult.getResponse().getContentAsString()).get("id").asLong();
		LocalDate date = LocalDate.now().plusWeeks(8).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));

		MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"serviceId":%d,"vehicleId":%d,"date":"%s","startTime":"09:00"}
						""".formatted(serviceId, vehicleId, date)))
				.andExpect(status().isOk())
				.andReturn();
		long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asLong();
		long customerBookingNotificationId = findNotificationId(
				getInbox(customerToken), "BOOKING", bookingId);
		long ownerBookingNotificationId = findNotificationId(
				getInbox(ownerToken), "BOOKING", bookingId);

		mockMvc.perform(put("/api/v1/notifications/" + ownerBookingNotificationId + "/read")
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"enabledCategories":["PAYMENT","LOYALTY","OFFER","REMINDER","REVIEW","SYSTEM","ANNOUNCEMENT"],"quietHoursStart":null,"quietHoursEnd":null,"globalMuted":false}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabledCategories").isArray());

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"enabledCategories":["BOOKING"],"quietHoursStart":"22:00","quietHoursEnd":null,"globalMuted":false}
						"""))
				.andExpect(status().isBadRequest());

		MvcResult secondVehicleResult = mockMvc.perform(post("/api/v1/customers/me/vehicles")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"registrationNo":"NOTE2-%s","type":"SEDAN"}
						""".formatted(suffix)))
				.andExpect(status().isOk())
				.andReturn();
		long secondVehicleId = objectMapper.readTree(secondVehicleResult.getResponse().getContentAsString())
				.get("id").asLong();
		MvcResult secondBookingResult = mockMvc.perform(post("/api/v1/bookings")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"serviceId":%d,"vehicleId":%d,"date":"%s","startTime":"10:00"}
						""".formatted(serviceId, secondVehicleId, date)))
				.andExpect(status().isOk())
				.andReturn();
		long secondBookingId = objectMapper.readTree(secondBookingResult.getResponse().getContentAsString())
				.get("id").asLong();
		mockMvc.perform(get("/api/v1/notifications")
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.category == 'BOOKING' && @.targetId == " + secondBookingId + ")]").isEmpty());

		String announcementTitle = "Holiday hours " + suffix;
		mockMvc.perform(post("/api/v1/notifications/announcements")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"%s","message":"The shop will close early on the holiday."}
						""".formatted(announcementTitle)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/notifications")
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.title == '" + announcementTitle + "')]").isNotEmpty());

		mockMvc.perform(put("/api/v1/notifications/preferences")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"enabledCategories":["BOOKING","PAYMENT","LOYALTY","OFFER","REMINDER","REVIEW","SYSTEM","ANNOUNCEMENT"],"quietHoursStart":null,"quietHoursEnd":null,"globalMuted":true}
						"""))
				.andExpect(status().isOk());
		MvcResult importantBookingResult = mockMvc.perform(post("/api/v1/bookings")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"serviceId":%d,"vehicleId":%d,"date":"%s","startTime":"11:00"}
						""".formatted(serviceId, secondVehicleId, date)))
				.andExpect(status().isOk())
				.andReturn();
		long importantBookingId = objectMapper.readTree(importantBookingResult.getResponse().getContentAsString())
				.get("id").asLong();
		assertTrue(hasNotification(getInbox(customerToken), "BOOKING", importantBookingId));

		String mutedAnnouncementTitle = "Muted notice " + suffix;
		mockMvc.perform(post("/api/v1/notifications/announcements")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"%s","message":"This non-essential announcement is muted."}
						""".formatted(mutedAnnouncementTitle)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/notifications")
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.title == '" + mutedAnnouncementTitle + "')]").isEmpty());

		mockMvc.perform(post("/api/v1/notifications/announcements")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"Unauthorized","message":"Not allowed."}
						"""))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/notifications/" + customerBookingNotificationId + "/read")
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/notifications/" + customerBookingNotificationId)
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk());
	}

	@Test
	void writeRequestsRequireAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/services"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void customersCanRegisterAndOnlyAccessTheirOwnProtectedProfile() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String email = "customer-" + suffix + "@example.com";
		String phone = String.format("555%08d", Math.abs((long) suffix.hashCode()) % 100_000_000L);
		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Test Customer","email":"%s","phone":"%s","password":"test-password-123"}
						""".formatted(email, phone)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.role").value("CUSTOMER"))
				.andReturn();

		String token = objectMapper.readTree(registerResult.getResponse().getContentAsString())
				.get("token")
				.asText();

		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("CUSTOMER"))
				.andExpect(jsonPath("$.email").value(email));

		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/customers/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));

		mockMvc.perform(get("/api/v1/customers"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void customerCanCreateAndReadOnlyTheirOwnVehicles() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String phone = String.format("777%08d", Math.abs((long) suffix.hashCode()) % 100_000_000L);
		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Vehicle Owner","email":"vehicle-%s@example.com","phone":"%s","password":"vehicle-password-123"}
						""".formatted(suffix, phone)))
				.andExpect(status().isOk())
				.andReturn();

		String token = objectMapper.readTree(registerResult.getResponse().getContentAsString())
				.get("token")
				.asText();

		mockMvc.perform(post("/api/v1/customers/me/vehicles")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"registrationNo":"TEST-%s","type":"SEDAN"}
						""".formatted(suffix)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("SEDAN"));

		mockMvc.perform(get("/api/v1/customers/me/vehicles").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].registrationNo").value("TEST-" + suffix.toUpperCase()));
	}

	@Test
	void invalidBearerTokensAreNotAccepted() throws Exception {
		mockMvc.perform(get("/api/v1/customers/me").header("Authorization", "Bearer forged.token.value"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void browserPaymentPreflightAllowsTheIdempotencyHeader() throws Exception {
		mockMvc.perform(options("/api/v1/invoices/1/payments")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
						.string("Access-Control-Allow-Headers",
								org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key")));
	}

	@Test
	void onlyOwnersCanCreateStaffAccounts() throws Exception {
		MvcResult ownerLogin = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"owner@example.com","password":"owner-test-password-123"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.role").value("OWNER"))
				.andReturn();
		String ownerToken = objectMapper.readTree(ownerLogin.getResponse().getContentAsString())
				.get("token").asText();

		mockMvc.perform(post("/api/v1/staff")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"WashCare Staff","email":"staff@example.com","password":"staff-test-password-123"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("STAFF"));

		mockMvc.perform(post("/api/v1/customers")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Walk In Customer","email":"walk-in@example.com","phone":"555-010-3030","notes":"Prefers morning visits"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registrationSource").value("STAFF"));

		mockMvc.perform(get("/api/v1/customers").param("q", "Walk In")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Walk In Customer"));

		MvcResult customerRegistration = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Regular Customer","email":"regular@example.com","phone":"555-010-2020","password":"customer-test-password-123"}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		String customerToken = objectMapper.readTree(customerRegistration.getResponse().getContentAsString())
				.get("token").asText();

		mockMvc.perform(post("/api/v1/staff")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Unauthorized Staff","email":"not-allowed@example.com","password":"staff-test-password-123"}
						"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void onlyOwnersCanManageServicesAndCustomersCanBrowseTheActiveCatalog() throws Exception {
		String ownerToken = signInOwner();
		MvcResult createResult = mockMvc.perform(post("/api/v1/services")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Sensitive Detail","description":"A detailed wash.","price":850.00,"estimatedDurationMinutes":60,"active":true}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Sensitive Detail"))
				.andReturn();
		long serviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(put("/api/v1/services/" + serviceId)
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Sensitive Detail","description":"Updated package.","price":950.00,"estimatedDurationMinutes":75,"active":false}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.price").value(950.0));

		mockMvc.perform(get("/api/v1/services/manage/" + serviceId)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/v1/services")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + serviceId + ")]").isEmpty());

		String customerToken = registerCustomer(UUID.randomUUID().toString().substring(0, 8));
		mockMvc.perform(post("/api/v1/services")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Unauthorized service","description":"","price":10,"estimatedDurationMinutes":30}
						"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void bookingCapacityIsEnforcedAndReleasedOnCancellation() throws Exception {
		String ownerToken = signInOwner();
		MvcResult serviceResult = mockMvc.perform(post("/api/v1/services")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Capacity Test Wash","description":"Capacity validation service.","price":501.00,"estimatedDurationMinutes":60,"active":true}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		long serviceId = objectMapper.readTree(serviceResult.getResponse().getContentAsString()).get("id").asLong();

		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String firstToken = registerCustomer(suffix + "a");
		String secondToken = registerCustomer(suffix + "b");
		String thirdToken = registerCustomer(suffix + "c");
		long firstVehicleId = addCustomerVehicle(firstToken, "CAP-" + suffix + "A");
		long secondVehicleId = addCustomerVehicle(secondToken, "CAP-" + suffix + "B");
		long thirdVehicleId = addCustomerVehicle(thirdToken, "CAP-" + suffix + "C");
		LocalDate appointmentDate = LocalDate.now().plusWeeks(1)
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
		String date = appointmentDate.toString();

		mockMvc.perform(get("/api/v1/bookings/availability")
				.header("Authorization", "Bearer " + firstToken)
				.param("date", date)
				.param("serviceId", Long.toString(serviceId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.capacity").value(2))
				.andExpect(jsonPath("$.availableSlots[0]").value("09:00:00"));

		MvcResult firstBooking = mockMvc.perform(post("/api/v1/bookings")
				.header("Authorization", "Bearer " + firstToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"serviceId":%d,"vehicleId":%d,"date":"%s","startTime":"09:00","notes":null}
						""".formatted(serviceId, firstVehicleId, date)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.priceSnapshot").value(501.0))
				.andReturn();
		long firstBookingId = objectMapper.readTree(firstBooking.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(post("/api/v1/bookings")
				.header("Authorization", "Bearer " + secondToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"serviceId":%d,"vehicleId":%d,"date":"%s","startTime":"09:00"}
						""".formatted(serviceId, secondVehicleId, date)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/bookings")
				.header("Authorization", "Bearer " + thirdToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"serviceId":%d,"vehicleId":%d,"date":"%s","startTime":"09:00"}
						""".formatted(serviceId, thirdVehicleId, date)))
				.andExpect(status().isConflict());

		mockMvc.perform(put("/api/v1/services/" + serviceId)
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Capacity Test Wash","description":"Capacity validation service.","price":999.00,"estimatedDurationMinutes":60,"active":true}
						"""))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/bookings/me").header("Authorization", "Bearer " + firstToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].priceSnapshot").value(501.0));

		mockMvc.perform(post("/api/v1/bookings/" + firstBookingId + "/cancel")
				.header("Authorization", "Bearer " + firstToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		mockMvc.perform(post("/api/v1/bookings")
				.header("Authorization", "Bearer " + thirdToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"serviceId":%d,"vehicleId":%d,"date":"%s","startTime":"09:00"}
						""".formatted(serviceId, thirdVehicleId, date)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/bookings/me").header("Authorization", "Bearer " + secondToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].priceSnapshot").value(501.0));
	}

	@Test
	void completedBookingsCreateInvoicesAndPaymentsAreAuditedAndIdempotent() throws Exception {
		String ownerToken = signInOwner();
		MvcResult serviceResult = mockMvc.perform(post("/api/v1/services")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Billing Test Wash","description":"Billing workflow test.","price":750.00,"estimatedDurationMinutes":60,"active":true}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		long serviceId = objectMapper.readTree(serviceResult.getResponse().getContentAsString()).get("id").asLong();

		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String customerToken = registerCustomer("billing" + suffix);
		long vehicleId = addCustomerVehicle(customerToken, "BILL-" + suffix);
		LocalDate appointmentDate = LocalDate.now().plusWeeks(1)
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
		MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"serviceId":%d,"vehicleId":%d,"date":"%s","startTime":"09:00"}
						""".formatted(serviceId, vehicleId, appointmentDate)))
				.andExpect(status().isOk())
				.andReturn();
		long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asLong();

		for (String nextStatus : List.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED")) {
			mockMvc.perform(put("/api/v1/bookings/" + bookingId + "/status")
					.header("Authorization", "Bearer " + ownerToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"status":"%s"}
							""".formatted(nextStatus)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value(nextStatus));
		}

		MvcResult invoiceResult = mockMvc.perform(get("/api/v1/invoices/me")
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].bookingId").value(bookingId))
				.andExpect(jsonPath("$[0].totalAmount").value(750.0))
				.andExpect(jsonPath("$[0].balance").value(750.0))
				.andReturn();
		long invoiceId = objectMapper.readTree(invoiceResult.getResponse().getContentAsString())
				.get(0).get("id").asLong();
		String paymentKey = UUID.randomUUID().toString();

		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", paymentKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":250.00,"method":"UPI"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paidAmount").value(250.0))
				.andExpect(jsonPath("$.balance").value(500.0))
				.andExpect(jsonPath("$.payments.length()").value(1))
				.andExpect(jsonPath("$.payments[0].recordedBy").value("Shop Owner"));

		mockMvc.perform(get("/api/v1/loyalty/me").header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(0));

		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", paymentKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":250.00,"method":"UPI"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payments.length()").value(1));

		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", paymentKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":300.00,"method":"UPI"}
						"""))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
				.header("Authorization", "Bearer " + customerToken)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":500.00,"method":"CASH"}
						"""))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":501.00,"method":"CARD"}
						"""))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":500.00,"method":"CASH"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paidAmount").value(750.0))
				.andExpect(jsonPath("$.balance").value(0.0))
				.andExpect(jsonPath("$.payments.length()").value(2));

		mockMvc.perform(get("/api/v1/loyalty/me").header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(7))
				.andExpect(jsonPath("$.transactions.length()").value(1));

		mockMvc.perform(post("/api/v1/reviews")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"bookingId":%d,"rating":5,"comment":"Excellent wash."}
						""".formatted(bookingId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rating").value(5))
				.andExpect(jsonPath("$.customerName").value("Booking Customer billing" + suffix));

		mockMvc.perform(post("/api/v1/reviews")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"bookingId":%d,"rating":4}
						""".formatted(bookingId)))
				.andExpect(status().isConflict());

		mockMvc.perform(get("/api/v1/reviews")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].bookingId").value(bookingId));

		jdbcTemplate.update(
				"UPDATE booking_status_history SET changed_at = DATEADD('DAY', -91, CURRENT_TIMESTAMP) "
						+ "WHERE booking_id = ? AND new_status = 'COMPLETED'",
				bookingId);
		mockMvc.perform(get("/api/v1/reminders/me").header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].status").value("SENT"))
				.andExpect(jsonPath("$[0].channel").value("IN_APP"));
		mockMvc.perform(get("/api/v1/reminders/me").header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		assertTrue(hasNotification(getInbox(customerToken), "REMINDER", bookingId));
		assertTrue(hasNotification(getInbox(ownerToken), "REVIEW", bookingId));
	}

	@Test
	void ownerCanManageOffersAndCustomersCanOnlyViewActiveDatedOffers() throws Exception {
		String ownerToken = signInOwner();
		String customerToken = registerCustomer("offer" + UUID.randomUUID().toString().substring(0, 8));
		String startsAt = java.time.LocalDateTime.now().minusMinutes(1).withNano(0).toString();
		String endsAt = java.time.LocalDateTime.now().plusDays(7).withNano(0).toString();

		MvcResult offerResult = mockMvc.perform(post("/api/v1/offers")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Return Visit","description":"A little thank-you.","discountType":"PERCENT","discountValue":15,"startsAt":"%s","endsAt":"%s","eligibility":"One use per customer","active":true}
						""".formatted(startsAt, endsAt)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discountType").value("PERCENT"))
				.andReturn();
		long offerId = objectMapper.readTree(offerResult.getResponse().getContentAsString()).get("id").asLong();

		assertTrue(hasNotification(getInbox(customerToken), "OFFER", offerId));
		mockMvc.perform(get("/api/v1/offers").header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Return Visit"));

		mockMvc.perform(put("/api/v1/offers/" + offerId)
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Return Visit","description":"Paused offer.","discountType":"PERCENT","discountValue":15,"startsAt":"%s","endsAt":"%s","active":false}
						""".formatted(startsAt, endsAt)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/v1/offers").header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + offerId + ")]").isEmpty());

		mockMvc.perform(post("/api/v1/offers")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Unauthorized offer","discountType":"FIXED","discountValue":10,"startsAt":"%s","endsAt":"%s","active":true}
						""".formatted(startsAt, endsAt)))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/offers")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Invalid percentage","discountType":"PERCENT","discountValue":101,"startsAt":"%s","endsAt":"%s","active":true}
						""".formatted(startsAt, endsAt)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void loyaltyRedemptionUsesPointsOnceAndRespectsTheInvoiceLimit() throws Exception {
		String ownerToken = signInOwner();
		MvcResult serviceResult = mockMvc.perform(post("/api/v1/services")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Loyalty Test Wash","description":"Points accrual and redemption.","price":2000.00,"estimatedDurationMinutes":30,"active":true}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		long serviceId = objectMapper.readTree(serviceResult.getResponse().getContentAsString()).get("id").asLong();
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String customerToken = registerCustomer("loyalty" + suffix);
		long vehicleId = addCustomerVehicle(customerToken, "LOY-" + suffix);
		LocalDate firstDate = LocalDate.now().plusWeeks(1)
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
		long firstBooking = createCustomerBooking(customerToken, serviceId, vehicleId, firstDate, "09:00");
		mockMvc.perform(post("/api/v1/reviews")
				.header("Authorization", "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"bookingId":%d,"rating":5}
						""".formatted(firstBooking)))
				.andExpect(status().isConflict());
		completeBooking(ownerToken, firstBooking);
		long firstInvoice = invoiceIdForBooking(customerToken, firstBooking);
		mockMvc.perform(post("/api/v1/invoices/" + firstInvoice + "/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":2000.00,"method":"CARD"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customerLoyaltyPoints").value(20));
		assertTrue(hasNotification(getInbox(customerToken), "PAYMENT", firstBooking));
		assertTrue(hasNotification(getInbox(customerToken), "LOYALTY", null));

		long secondBooking = createCustomerBooking(customerToken, serviceId, vehicleId, firstDate.plusDays(7), "09:00");
		completeBooking(ownerToken, secondBooking);
		long secondInvoice = invoiceIdForBooking(customerToken, secondBooking);

		mockMvc.perform(post("/api/v1/loyalty/invoices/" + secondInvoice + "/redeem")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"points":20}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discount").value(2.0))
				.andExpect(jsonPath("$.totalAmount").value(1998.0))
				.andExpect(jsonPath("$.remainingPoints").value(0));

		mockMvc.perform(post("/api/v1/loyalty/invoices/" + secondInvoice + "/redeem")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"points":20}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discount").value(2.0));

		mockMvc.perform(post("/api/v1/loyalty/invoices/" + secondInvoice + "/redeem")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"points":1}
						"""))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/invoices/" + secondInvoice + "/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":1998.00,"method":"CASH"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(0.0))
				.andExpect(jsonPath("$.customerLoyaltyPoints").value(19));
	}

	@Test
	void ownerAnalyticsReportsPaidRevenueAndCompletedVisitsButIsOwnerOnly() throws Exception {
		String ownerToken = signInOwner();
		String customerToken = registerCustomer("analytics" + UUID.randomUUID().toString().substring(0, 8));
		MvcResult serviceResult = mockMvc.perform(post("/api/v1/services")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Analytics Test Wash","description":"Analytics report coverage.","price":1200.00,"estimatedDurationMinutes":30,"active":true}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		long serviceId = objectMapper.readTree(serviceResult.getResponse().getContentAsString()).get("id").asLong();
		long vehicleId = addCustomerVehicle(customerToken,
				"ANA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
		LocalDate appointmentDate = LocalDate.now().plusWeeks(1)
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
		long bookingId = createCustomerBooking(customerToken, serviceId, vehicleId, appointmentDate, "09:00");
		completeBooking(ownerToken, bookingId);
		long invoiceId = invoiceIdForBooking(customerToken, bookingId);
		mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":1200.00,"method":"CASH"}
						"""))
				.andExpect(status().isOk());
		jdbcTemplate.update("UPDATE bookings SET booking_date = CURRENT_DATE WHERE id = ?", bookingId);

		mockMvc.perform(get("/api/v1/analytics/owner").param("days", "30")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paidRevenue").value(org.hamcrest.Matchers.greaterThan(0.0)))
				.andExpect(jsonPath("$.completedServices").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.uniqueCustomers").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.todayBookings").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.customerProfiles").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.popularServices[?(@.serviceName == 'Analytics Test Wash')]").isNotEmpty());

		mockMvc.perform(get("/api/v1/analytics/owner").header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/analytics/owner"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/analytics/owner")
				.param("days", "3")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isBadRequest());
	}

	private long createCustomerBooking(String token, long serviceId, long vehicleId, LocalDate date, String startTime)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/bookings")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"serviceId":%d,"vehicleId":%d,"date":"%s","startTime":"%s"}
						""".formatted(serviceId, vehicleId, date, startTime)))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	private void completeBooking(String ownerToken, long bookingId) throws Exception {
		for (String nextStatus : List.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED")) {
			mockMvc.perform(put("/api/v1/bookings/" + bookingId + "/status")
					.header("Authorization", "Bearer " + ownerToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"status":"%s"}
							""".formatted(nextStatus)))
					.andExpect(status().isOk());
		}
	}

	private long invoiceIdForBooking(String customerToken, long bookingId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/invoices/me")
				.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andReturn();
		for (var invoice : objectMapper.readTree(result.getResponse().getContentAsString())) {
			if (invoice.get("bookingId").asLong() == bookingId) {
				return invoice.get("id").asLong();
			}
		}
		throw new AssertionError("Invoice not found for completed booking " + bookingId);
	}

	private String getInbox(String token) throws Exception {
		return mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
	}

	private long findNotificationId(String inboxJson, String category, long targetId) throws Exception {
		for (var notification : objectMapper.readTree(inboxJson).get("items")) {
			if (notification.get("category").asText().equals(category)
					&& notification.has("targetId")
					&& notification.get("targetId").asLong() == targetId) {
				return notification.get("id").asLong();
			}
		}
		throw new AssertionError("Notification not found for " + category + " target " + targetId);
	}

	private boolean hasNotification(String inboxJson, String category, Long targetId) throws Exception {
		for (var notification : objectMapper.readTree(inboxJson).get("items")) {
			if (notification.get("category").asText().equals(category)
					&& (targetId == null || notification.has("targetId")
							&& notification.get("targetId").asLong() == targetId)) {
				return true;
			}
		}
		return false;
	}

	private String signInOwner() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"owner@example.com","password":"owner-test-password-123"}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
	}

	private String registerCustomer(String suffix) throws Exception {
		String phoneSuffix = String.format("%010d", Math.abs((long) suffix.hashCode()) % 10_000_000_000L);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Booking Customer %s","email":"booking-%s@example.com","phone":"9%s","password":"booking-test-password-123"}
						""".formatted(suffix, suffix, phoneSuffix)))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
	}

	private long addCustomerVehicle(String token, String registrationNo) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/customers/me/vehicles")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"registrationNo":"%s","type":"SEDAN"}
						""".formatted(registrationNo)))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}
}
