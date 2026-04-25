refer front end in https://github.com/Rahul-150705/client-connect-hub

# 🚀 Renew AI — Insurance Renewal Automation Platform

> Automate insurance renewals, eliminate missed follow-ups, and improve client retention with intelligent messaging workflows.

---

## 📌 Overview

**Renew AI** is a full-stack insurance renewal automation system designed to help agents manage policies and remove manual reminder tracking.

It automatically detects expiring policies and sends reminders via **WhatsApp or SMS**, ensuring no renewal opportunity is missed.

The system is built with **production-level backend concepts** such as retry mechanisms, idempotency, stateless authentication, and scheduler-driven workflows.

---

## 🎯 Real-World Impact

- 💰 Prevents revenue loss from missed renewals
- ⏱️ Eliminates manual follow-up tracking
- 📈 Improves customer retention with timely reminders
- 🧠 Demonstrates real SaaS backend architecture (scheduler + retry + messaging system)

---

## 🖥️ Demo & Screenshots

### 🎥 Demo Video
[Watch Demo](https://your-demo-link.com)

---

### 📸 Screenshots

#### Dashboard
![Dashboard](./screenshots/dashboard.png)

#### Create Policy
![Create Policy](./screenshots/create-policy.png)

#### Message Logs
![Message Logs](./screenshots/message-logs.png)

#### Login Page
![Login](./screenshots/login.png)

---

## ✨ Key Features

### 🔐 Authentication & Security
- JWT-based authentication (stateless sessions)
- BCrypt password hashing
- Agent-scoped data isolation (multi-tenant safe)

---

### 📋 Policy Management
- Full CRUD operations for insurance policies
- Tracks client details, premium, insurer, and expiry dates
- Manual renewal workflow (renewed / lost clients)

---

### ⏰ Smart Reminder System
- Automated reminders at:
  - 7 days before expiry
  - 3 days before expiry
  - On expiry day
- Cron-based scheduling using Spring `@Scheduled`

---

### 💬 Messaging Engine
- WhatsApp-first delivery strategy
- SMS fallback mechanism
- Twilio integration (mock mode available for development)

---

### 🔁 Retry Mechanism
- Retry failed messages up to **3 times**
- Tracks:
  - Retry count
  - Failure reason
  - Last attempt timestamp
- Prevents duplicate sends using idempotency checks

---

### 🧠 Idempotency & Data Integrity
- Composite unique constraint: `(policy_id, reminder_type, channel)`
- Ensures no duplicate reminders are sent

---

## 🏗️ Architecture

```
React Frontend
      ↓
Spring Boot Backend
      ↓
PostgreSQL Database
      ↓
Scheduler Service
      ↓
Twilio API (SMS / WhatsApp)
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React |
| Backend | Spring Boot 3 |
| Security | Spring Security + JWT |
| Database | PostgreSQL (Neon DB) |
| ORM | Spring Data JPA / Hibernate |
| Messaging | Twilio (SMS & WhatsApp) |
| Scheduler | Spring `@Scheduled` |
| Build Tool | Maven |
| Java | Java 17+ |

---

## 📂 Project Structure

```
src/main/java/com/renewai/
├── config/
├── controller/
├── dto/
├── entity/
├── repository/
├── service/
└── util/
```

---

## ⚙️ Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL (or Neon DB)
- Twilio account (optional)

---

### 1. Clone Repository

```bash
git clone https://github.com/your-org/renew-ai.git
cd renew-ai
```

### 2. Environment Variables

Create a `.env` file:

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

### 3. Run Application

```bash
mvn clean install
mvn spring-boot:run
```

Server runs at: **http://localhost:8080**

---

## 📡 API Overview

### 🔓 Public Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Agent login |
| `POST` | `/api/auth/register` | Register agent |
| `GET` | `/api/auth/health` | Health check |

### 🔒 Protected Endpoints

**Policies**

```
GET    /api/policies
POST   /api/policies/create
PUT    /api/policies/{id}/status
POST   /api/policies/{id}/manual-renew
DELETE /api/policies/{id}
```

**Messages**

```
GET  /api/messages/logs
POST /api/messages/send-bulk
POST /api/messages/{id}/retry
```

---

## ⏳ Scheduler Jobs

| Time | Job |
|---|---|
| 8:00 AM | Mark expired policies |
| 9:00 AM | Send renewal reminders |

Reminder types: `SEVEN_DAYS` · `THREE_DAYS` · `EXPIRY_DAY`

---

## 🔁 Retry Logic

- Only `FAILED` messages can be retried
- Maximum retry limit: **3 attempts**
- Prevents duplicate sends if already successful
- Tracks failure reasons for debugging

---

## 🧠 Architecture Decisions

- **Stateless Authentication** → scalable and horizontally scalable
- **Idempotency via DB constraints** → avoids duplicate reminders
- **WhatsApp-first strategy** → avoids multi-channel spam
- **Mock mode support** → enables local development without Twilio
- **Agent-scoped data** → secure multi-user system

---

## 🚀 Future Improvements

- 📊 Analytics dashboard (renewal success rate, delivery stats)
- 🤖 AI-based message personalization (LLM integration)
- 📅 Smart reminder optimization based on user behavior
- 🌐 Multi-tenant SaaS deployment
- 📲 Mobile app integration

---

## 🧑‍💻 Author

**Rahul P**

---

## ⭐ Support

If you found this project useful, consider giving it a ⭐ on GitHub!
