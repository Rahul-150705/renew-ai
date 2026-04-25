refer front end in https://github.com/Rahul-150705/client-connect-hub

# Renew AI — Insurance Renewal Automation Platform

A Spring Boot backend that automates insurance policy renewal reminders via SMS and WhatsApp, with JWT-based agent authentication, message deduplication, and a retry mechanism for failed deliveries.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Scheduler](#scheduler)
- [Message Retry Mechanism](#message-retry-mechanism)
- [Architecture Decisions](#architecture-decisions)

---

## Overview

Renew AI helps insurance agents manage their client policies and automate renewal reminders. When a policy is approaching expiry, the system automatically sends reminder messages via SMS (Twilio) or WhatsApp. Agents can log in, manage policies, view message delivery logs, and manually handle renewals when automation falls short.

---

## Features

- **JWT Authentication** — Secure agent login and registration with BCrypt password hashing
- **Policy Management** — Create, update, delete, and view insurance policies with full client details
- **Automated Reminders** — Spring Scheduler sends reminders 7 days, 3 days, and on the day of expiry
- **WhatsApp & SMS** — Routes messages to WhatsApp if available, falls back to SMS
- **Message Deduplication** — Composite unique constraint prevents duplicate messages per policy/channel
- **Retry Mechanism** — Failed messages can be retried up to 3 times via API
- **Policy Expiry Tracking** — Daily job marks policies as EXPIRED automatically
- **Manual Renewal Workflow** — Agents can log manual renewals or remove lost clients

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.x |
| Security | Spring Security + JWT (jjwt) |
| Database | PostgreSQL (Neon DB) |
| ORM | Spring Data JPA / Hibernate |
| Messaging | Twilio (SMS & WhatsApp) |
| Scheduler | Spring `@Scheduled` |
| Build | Maven |
| Java Version | Java 17+ |

---

## Project Structure

```
src/main/java/com/renewai/
├── config/
│   ├── JwtAuthenticationFilter.java   # Intercepts requests and validates JWT
│   └── SecurityConfig.java            # Security rules, CORS, session policy
├── controller/
│   ├── AuthController.java            # POST /api/auth/login, /register
│   ├── PolicyController.java          # CRUD for policies
│   └── MessageLogController.java      # Message logs, retry, bulk send
├── dto/                               # Request/Response data transfer objects
├── entity/
│   ├── Agent.java                     # Insurance agent (system user)
│   ├── Client.java                    # Policy holder
│   ├── Policy.java                    # Insurance policy
│   └── MessageLog.java                # Sent message record with retry state
├── repository/                        # Spring Data JPA repositories
├── service/
│   ├── AuthService.java               # Login and registration logic
│   ├── PolicyService.java             # Policy CRUD and business logic
│   ├── MessageService.java            # Message generation and Twilio dispatch
│   └── RenewalSchedulerService.java   # Scheduled jobs
└── util/
    └── JwtUtil.java                   # Token generation and validation
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL database (or a Neon DB connection string)
- Twilio account (optional — mock mode works without it)

### 1. Clone the repository

```bash
git clone https://github.com/your-org/renew-ai.git
cd renew-ai
```

### 2. Set up environment variables

Create a `.env` file (or set these as system environment variables):

```env
DB_URL=jdbc:postgresql://<host>/<dbname>?sslmode=require
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password

JWT_SECRET=your_secret_key_min_32_characters_long

TWILIO_ENABLED=false
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=your_auth_token
TWILIO_PHONE_NUMBER=+1234567890
TWILIO_WHATSAPP_NUMBER=+1234567890

ALLOWED_ORIGINS=http://localhost:3000
```

### 3. Build and run

```bash
mvn clean install
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## Configuration

Key settings in `application.properties`:

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `jwt.expiration` | `86400000` | Token TTL in ms (24 hours) |
| `renewal.scheduler.cron` | `0 0 9 * * ?` | Reminder job — runs daily at 9:00 AM |
| `twilio.enabled` | `false` | Set to `true` to send real messages |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema management strategy |

When `twilio.enabled=false`, all messages are logged to console in mock mode — useful for local development.

---

## API Reference

All protected endpoints require the `Authorization: Bearer <token>` header.

### Authentication (Public)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Agent login — returns JWT token |
| `POST` | `/api/auth/register` | Register a new agent |
| `GET` | `/api/auth/health` | Service health check |

**Login request body:**
```json
{
  "username": "agent1",
  "password": "yourpassword"
}
```

**Login response:**
```json
{
  "token": "eyJhbGci...",
  "tokenType": "Bearer",
  "agentId": 1,
  "username": "agent1",
  "fullName": "John Smith",
  "email": "john@example.com"
}
```

### Policies (Protected)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/policies` | List all policies for the logged-in agent |
| `POST` | `/api/policies/create` | Create a new policy with client details |
| `GET` | `/api/policies/{id}` | Get a single policy by ID |
| `PUT` | `/api/policies/{id}/status` | Update policy status |
| `POST` | `/api/policies/{id}/manual-renew` | Mark as manually renewed or lost |
| `DELETE` | `/api/policies/{id}` | Delete a policy |

**Create policy request body:**
```json
{
  "clientFullName": "Jane Doe",
  "clientEmail": "jane@example.com",
  "clientPhoneNumber": "+919876543210",
  "clientWhatsappNumber": "+919876543210",
  "policyNumber": "POL-2024-001",
  "policyType": "VEHICLE",
  "vehicleType": "Car",
  "registrationNumber": "TN01AB1234",
  "insurerName": "HDFC Ergo",
  "startDate": "2024-01-01",
  "expiryDate": "2025-01-01",
  "premium": 15000.00,
  "premiumFrequency": "YEARLY"
}
```

### Messages (Protected)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/messages/logs` | Retrieve all message logs (newest first) |
| `POST` | `/api/messages/send-bulk` | Manually trigger the renewal scheduler |
| `POST` | `/api/messages/test-scheduler` | Alias for manual scheduler trigger |
| `POST` | `/api/messages/{id}/retry` | Retry a failed message |

---

## Scheduler

Two cron jobs run automatically every day:

**8:00 AM — Expiry Job** (`updateExpiredPolicies`)
Scans all active policies and marks any that have passed their expiry date as `EXPIRED`.

**9:00 AM — Reminder Job** (`checkExpiringPoliciesAndSendReminders`)
Sends reminders in three waves:
- Policies expiring in **7 days** → `SEVEN_DAYS` reminder
- Policies expiring in **3 days** → `THREE_DAYS` reminder
- Policies expiring **today** → `EXPIRY_DAY` final notice

If a reminder has already been sent for a given policy/type/channel combination, it is skipped automatically (idempotent).

---

## Message Retry Mechanism

Failed messages can be retried via `POST /api/messages/{id}/retry`.

Business rules:
- Only messages with status `FAILED` can be retried
- Maximum **3 retry attempts** per message (`MessageLog.MAX_RETRY_COUNT`)
- Each attempt updates `retryCount`, `lastAttemptAt`, and `failureReason`
- If a `SENT` record already exists for the same policy/reminder/channel, the retry is rejected (idempotency guard)

The `MessageLogDto` response includes helper fields for the frontend:

```json
{
  "id": 5,
  "status": "FAILED",
  "retryCount": 2,
  "canRetry": true,
  "maxRetriesExhausted": false,
  "failureReason": "Twilio returned failure status",
  "lastAttemptAt": "2026-04-21T10:30:00"
}
```

---

## Architecture Decisions

**Stateless sessions** — JWT tokens are validated on every request. No server-side session state is stored, making horizontal scaling straightforward.

**Message deduplication** — A composite unique constraint on `(policy_id, reminder_type, channel)` at the database level prevents duplicate reminders even if the scheduler runs more than once.

**WhatsApp-first routing** — If a client has a WhatsApp number, only WhatsApp is used. SMS is the fallback. This avoids sending the same reminder twice to the same person on different channels.

**Mock mode** — Setting `twilio.enabled=false` routes all messages to the application log instead of Twilio. This makes local development and testing possible without any Twilio credentials.

**Agent-scoped data** — All policies are fetched through the agent context (`Authentication.getName()`), so agents can only see their own clients and policies.

**Manual renewal workflow** — When automated messaging fails and the agent contacts the customer directly, they can mark the policy as `MANUAL_RENEWED`. If the client did not renew, the policy is deleted, keeping the database clean.
