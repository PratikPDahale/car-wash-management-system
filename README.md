# WashCare CRM

WashCare is a single-shop car wash CRM built from the project blueprint. The current milestone includes a React client, Spring Boot API, customer login with signed JWTs, owner/staff/customer authorization, customer and vehicle records, an owner-managed service catalog, and online/walk-in bookings with configurable shop scheduling.

## Stack

- React 19, TypeScript, Vite
- Java 17, Spring Boot 4, Spring Security, Spring Data JPA
- H2 for local development; MySQL 8 for a persistent database

## Run locally

### API

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The API starts on `http://localhost:8080`. The local profile uses an in-memory H2 database and seeds a starter wash-service catalog. Service browsing, customer registration, and login are public; CRM data and staff account creation require a signed-in role with the appropriate permission.

To create the first owner in the local H2 database, set credentials before starting the API:

```powershell
$env:WASHCARE_OWNER_EMAIL = "owner@example.com"
$securePassword = Read-Host "Choose an owner password (10+ characters)" -AsSecureString
$env:WASHCARE_OWNER_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password
.\mvnw.cmd spring-boot:run
```

The owner bootstrap runs only when that email is not already registered. H2 is in-memory, so its accounts and CRM records are reset when the API stops. If `WASHCARE_JWT_SECRET` is omitted locally, the API generates a random in-memory key and logs that tokens will expire when it restarts. For deployment, set `WASHCARE_JWT_SECRET` to a stable, random secret of at least 32 bytes.

To use MySQL, run `database/schema.sql` from the repository root (it creates the schema and starter services), provide `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`, then start the API with the `mysql` profile:

```powershell
$env:SPRING_PROFILES_ACTIVE = "mysql"
.\mvnw.cmd spring-boot:run
```

### Run the containerized app

Docker Desktop with Compose v2 is required. Copy `.env.example` to `.env`, replace every placeholder with unique secrets, then start the services from the repository root:

```powershell
Copy-Item .env.example .env
notepad .env
docker compose up --build -d
docker compose ps
```

Open `http://localhost`. Nginx serves the frontend and proxies `/api/` requests to Spring Boot. MySQL uses a named volume so its records survive container restarts; the initial schema is imported only when this volume is first created. The local owner account is bootstrapped from `.env` on the initial database startup. To inspect service logs, run `docker compose logs -f backend frontend`. To stop without deleting data, run `docker compose down`. Never commit `.env`; to intentionally erase the local database and all its records, remove the named volume with `docker compose down -v`.

This Compose setup is for local development/demo deployment, not a production internet-facing deployment. Before public deployment, use managed MySQL, a TLS-terminating reverse proxy, restrictive network/firewall rules, managed secrets, backups, and a production review of account bootstrap and operational settings.

The MySQL profile validates the JPA-managed account, customer, vehicle, service, schedule, booking, booking-history, invoice, payment, loyalty, offer, reminder, review, and notification tables against the database. The full relational schema is included as the baseline for the remaining modules. If you created the earlier version of the schema before the CRM foundation, apply [V2__identity_and_customer_profiles.sql](./database/migrations/V2__identity_and_customer_profiles.sql), [V3__shop_schedule_and_booking_status_history.sql](./database/migrations/V3__shop_schedule_and_booking_status_history.sql), [V4__invoices_payments.sql](./database/migrations/V4__invoices_payments.sql), [V5__retention_and_feedback.sql](./database/migrations/V5__retention_and_feedback.sql), and [V6__in_app_notifications.sql](./database/migrations/V6__in_app_notifications.sql) in order before starting with MySQL.

The starter schedule is Tuesday–Sunday, 9:00 AM–6:00 PM, with 30-minute start-time intervals, capacity for two simultaneous vehicles, and customer cancellation allowed until the appointment starts. Slot availability is checked when displayed and rechecked in a serialized database transaction when creating a booking. Change the initial values in `ShopScheduleBootstrap` and `database/schema.sql` before starting a shop with different hours; existing H2 schedules are retained once created.

### Web client

```powershell
cd frontend
npm install
npm run dev
```

The client opens at `http://localhost:5173`. Customers can create their own accounts; the shop owner can sign in with the bootstrap account. The access token is kept in the current browser tab's session storage, so refreshing preserves the session; signing out or closing the tab clears it. The frontend validates the token with the API when restoring a session. In local development, restarting the API invalidates tokens if `WASHCARE_JWT_SECRET` is not configured. Set `VITE_API_URL` in a local `.env` file to point at a different API URL; see `frontend/.env.example`.

### Current API routes

| Method | Route | Access |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Public customer registration |
| `POST` | `/api/v1/auth/login` | Public sign-in |
| `GET` | `/api/v1/auth/me` | Signed-in user, validate and restore the current session |
| `GET` | `/api/v1/services` | Public active service catalog |
| `GET` | `/api/v1/services/manage` | Owner, all services including paused packages |
| `POST`, `PUT` | `/api/v1/services` | Owner, create or update catalog services |
| `GET` | `/api/v1/services/manage/{id}` | Owner |
| `POST` | `/api/v1/staff` | Owner |
| `GET`, `POST`, `PUT` | `/api/v1/customers` | Owner or staff (`GET` supports `?q=` search) |
| `GET`, `PUT` | `/api/v1/customers/me` | Signed-in customer, own profile |
| `GET`, `POST` | `/api/v1/customers/me/vehicles` | Signed-in customer, own vehicles |
| `GET`, `POST` | `/api/v1/customers/{id}/vehicles` | Owner or staff |
| `GET` | `/api/v1/bookings/availability?date=YYYY-MM-DD&serviceId={id}` | Signed-in user |
| `POST` | `/api/v1/bookings` | Customer, own vehicle |
| `POST` | `/api/v1/bookings/walk-in` | Owner or staff |
| `GET` | `/api/v1/bookings/me` | Customer, own bookings |
| `GET` | `/api/v1/bookings?date=YYYY-MM-DD` | Owner or staff |
| `POST` | `/api/v1/bookings/{id}/cancel` | Customer, own eligible booking |
| `PUT` | `/api/v1/bookings/{id}/status` | Owner or staff, valid service lifecycle transitions |
| `GET` | `/api/v1/invoices` | Owner or staff, shop invoices |
| `GET` | `/api/v1/invoices/me` | Customer, own invoices and payment history |
| `POST` | `/api/v1/invoices/{id}/payments` | Owner or staff, record an in-shop payment |
| `GET` | `/api/v1/loyalty/me` | Customer, own points balance and ledger |
| `POST` | `/api/v1/loyalty/invoices/{id}/redeem` | Owner or staff, apply customer points to an unpaid invoice |
| `GET`, `POST`, `PUT` | `/api/v1/offers` | Signed-in user can view current offers; owner can create/update offers |
| `GET` | `/api/v1/offers/manage` | Owner, all offers including inactive/expired |
| `GET`, `POST` | `/api/v1/reviews` | Owner/staff view shop reviews; customer submits eligible review |
| `GET` | `/api/v1/reviews/me` | Customer, own reviews |
| `GET` | `/api/v1/reminders/me` | Customer, own in-app return reminders |
| `GET` | `/api/v1/analytics/owner?days=30` | Owner-only paid revenue, visits, repeat customers, ratings, and service popularity (`days` 7–365) |
| `GET` | `/api/v1/search?q={term}` | Signed-in global search across permitted customers, bookings, vehicles, and services (minimum 2 characters) |
| `GET` | `/api/v1/notifications` | Signed-in user, latest 50 inbox notifications and unread count |
| `PUT` | `/api/v1/notifications/{id}/read`, `/unread` | Signed-in user, update own notification read state |
| `PUT` | `/api/v1/notifications/read-all` | Signed-in user, mark all currently available notifications read |
| `DELETE` | `/api/v1/notifications/{id}` | Signed-in user, delete an own notification |
| `GET`, `PUT` | `/api/v1/notifications/preferences` | Signed-in user, category controls, quiet hours, and global mute |
| `POST` | `/api/v1/notifications/announcements` | Owner, send an in-app announcement to customers and staff |

Owner analytics supports 7–365-day reporting periods and summarizes paid revenue, bookings, completed visits, repeat-customer share, review ratings, and popular services. The owner overview also shows today's paid revenue/bookings and the total customer profile count.

The global search dialog opens from the header or with `Ctrl+K` (`⌘K` on macOS), searches after two characters, and shows up to five results per category. Owners/staff can search shop customers, vehicles, and bookings; customers only receive their own vehicles and bookings. Services are searchable by all signed-in roles. Selecting a result opens its workspace with the search applied.

The customer-only Help & Support workspace provides searchable FAQs, task guides, and shop policies. Direct contact details and support request submission are not configured.

The dashboard Bell stores in-app notifications for owner, staff, and customer accounts. Booking, payment, loyalty, offer, reminder, review, and account events are routed to relevant users; owners can also send announcements to customers and staff. Users can mark or delete notifications, filter to unread items, and configure categories, quiet hours, and a global mute for non-essential alerts. Quiet-hour notifications are held until quiet hours end; important alerts are not globally muted. Notifications are retained until deleted; the inbox displays the latest 50 and the unread count includes all available unread items. Email, SMS, and browser push are not configured.

New registrations do not automatically link to existing walk-in profiles. The API rejects duplicate email or phone records and requires the shop to verify/link an existing profile rather than risk linking the wrong person.

Completing a booking creates one invoice from its stored booking price; catalog price changes do not affect existing bills. Staff can record full or partial cash, UPI, or card payments. Payments are serialized against the invoice balance, retain the recording staff member and timestamp, and require an `Idempotency-Key` header so safe retries cannot duplicate a payment. Customers earn one point per ₹100 actually paid when the invoice is fully settled. Staff can apply points before recording any payment: 100 points equal ₹10 off, capped at 20% of the invoice subtotal, and each invoice can receive one redemption. No online payment gateway is used.

Owners can publish fixed-amount or percentage offers with validity dates; customers can view active offers, but they are not automatically applied to invoices. Customers can submit one 1–5-star review for each completed booking, and owners/staff can read shop feedback. An in-app return reminder is generated 90 days after a customer's latest completed visit if there has been no newer completed visit. The daily reminder job and customer reminder view are idempotent; email/SMS delivery is not configured.

### Verify

```powershell
cd backend
.\mvnw.cmd test
cd ..\frontend
npm run build
```



Owner and staff identities are provisioned by the owner; public registration always creates a customer account and cannot select a privileged role.
