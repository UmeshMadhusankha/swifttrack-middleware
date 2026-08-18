# SwiftTrack Middleware

Middleware solution for integrating three legacy systems of **SwiftLogistics**, a hypothetical logistics company. An order enters the system through a single API gateway and is driven through a Client Management System (CMS), a Warehouse Management System (WMS) and a Route Optimization System (ROS), each speaking a completely different protocol. If any step fails, all previous steps are automatically undone using the **SAGA pattern**.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Services in Detail](#services-in-detail)
  - [Gateway Service](#1-gateway-service)
  - [Auth Service](#2-auth-service)
  - [Order Service](#3-order-service)
  - [SAGA Orchestrator](#4-saga-orchestrator)
  - [Adapters](#5-adapters)
  - [Mock Legacy Systems](#6-mock-legacy-systems)
  - [Frontend Portal](#7-frontend-portal)
- [Infrastructure](#infrastructure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
  - [Quick Start (Docker Compose)](#quick-start-docker-compose)
  - [Running Services Individually](#running-services-individually)
- [Environment Variables](#environment-variables)
- [API Reference](#api-reference)
- [Message Flow (RabbitMQ)](#message-flow-rabbitmq)
- [Order Lifecycle](#order-lifecycle)
- [Database Schema](#database-schema)
- [Testing Failure Scenarios](#testing-failure-scenarios)
- [Useful URLs](#useful-urls)
- [Troubleshooting](#troubleshooting)

---

## Architecture Overview

```
                    ┌──────────────┐
                    │   Browser    │
                    │  (Next.js)   │
                    │  :3000       │
                    └──────┬───────┘
                           │ HTTP
                    ┌──────┴───────┐
                    │   Gateway    │
                    │  (Spring)    │
                    │  :8080       │
                    └──┬───────┬───┘
            ┌──────────┘       └──────────┐
            │                             │
     ┌──────┴───────┐             ┌───────┴──────┐
     │ Auth Service │             │ Order Service │
     │   :8086      │             │   :8081       │
     └──────────────┘             └───────┬───────┘
                                          │ RabbitMQ
                                  ┌───────┴───────┐
                                  │     SAGA      │
                                  │ Orchestrator  │
                                  │   :8082       │
                                  └───┬───┬───┬───┘
                                      │   │   │  RabbitMQ
                         ┌────────────┘   │   └────────────┐
                         │                │                │
                  ┌──────┴─────┐   ┌──────┴─────┐   ┌─────┴──────┐
                  │CMS Adapter │   │WMS Adapter │   │ROS Adapter │
                  │  :8084     │   │  :8083     │   │  :8085     │
                  └──────┬─────┘   └──────┬─────┘   └─────┬──────┘
                         │                │                │
                    SOAP/XML         Raw TCP          REST/JSON
                         │                │                │
                  ┌──────┴─────┐   ┌──────┴─────┐   ┌─────┴──────┐
                  │  CMS Mock  │   │  WMS Mock  │   │  ROS Mock  │
                  │  :3002     │   │  :9090     │   │  :3001     │
                  └────────────┘   └────────────┘   └────────────┘
```

All services communicate over a private Docker bridge network. The only ports exposed to the host are the gateway (`:8080`) and the frontend portal (`:3000`).

---

## Tech Stack

| Layer               | Technology                                      |
| ------------------- | ----------------------------------------------- |
| **Frontend**        | Next.js 15, React 19                            |
| **API Gateway**     | Spring Cloud Gateway (reactive, WebFlux)         |
| **Auth**            | Spring Boot 3.5, JJWT 0.12.6, BCrypt           |
| **Order Service**   | Spring Boot 3.5, Spring Data JPA, PostgreSQL     |
| **Orchestrator**    | Spring Boot 3.5, Spring Data JPA, RabbitMQ       |
| **CMS Adapter**     | Spring Boot 3.5, Spring WS (SOAP), JAXB          |
| **WMS Adapter**     | Spring Boot 3.5, raw `java.net` TCP sockets      |
| **ROS Adapter**     | Spring Boot 3.5, WebClient (WebFlux)             |
| **Mock CMS**        | Node.js, Express, node-soap                     |
| **Mock WMS**        | C++ 17, raw POSIX TCP sockets, CMake            |
| **Mock ROS**        | Node.js, Express                                |
| **Message Broker**  | RabbitMQ 3.13                                   |
| **Database**        | PostgreSQL 16                                   |
| **Containerization**| Docker, Docker Compose                          |
| **Java Version**    | 21 (Eclipse Temurin)                            |
| **Build Tool**      | Maven (Java services), npm (Node services), CMake (C++ WMS) |

---

## Project Structure

```
swifttrack-middleware/
├── docker-compose.yml          # Orchestrates the full stack
├── postgres-init.sql           # Creates per-service databases on first run
├── .gitignore
│
├── gateway-service/            # API Gateway (Spring Cloud Gateway)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       └── main/java/.../gateway/
│           ├── GatewayApplication.java
│           ├── config/
│           │   ├── CorsConfig.java         # CORS for frontend origin
│           │   └── RouteConfig.java         # /api/auth → auth-service, /api/orders → order-service
│           └── security/
│               ├── JwtAuthenticationFilter.java  # Validates JWT, injects X-Client-Id header
│               └── JwtVerifier.java              # HMAC-SHA signature verification
│
├── auth-service/               # Authentication & JWT issuance
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       └── main/java/.../authservice/
│           ├── AuthServiceApplication.java
│           ├── api/
│           │   ├── AuthController.java          # POST /api/auth/login
│           │   ├── AuthExceptionHandler.java    # 401 on bad credentials
│           │   └── dto/
│           │       ├── LoginRequest.java
│           │       └── LoginResponse.java
│           ├── config/
│           │   ├── PasswordEncoderConfig.java   # BCrypt bean
│           │   └── TestUserSeeder.java          # Seeds a default user on startup
│           ├── domain/
│           │   └── AppUser.java                 # JPA entity
│           ├── repository/
│           │   └── AppUserRepository.java
│           ├── security/
│           │   └── JwtIssuer.java               # Signs JWTs with HS256
│           └── service/
│               ├── AuthService.java
│               └── InvalidCredentialsException.java
│
├── order-service/              # Order creation & status tracking
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       └── main/java/.../orderservice/
│           ├── OrderServiceApplication.java
│           ├── api/
│           │   ├── OrderController.java         # POST & GET /api/orders
│           │   └── dto/
│           │       ├── CreateOrderRequest.java
│           │       └── OrderResponse.java
│           ├── domain/
│           │   ├── Order.java                   # JPA entity
│           │   └── OrderStatus.java             # PENDING → COMPLETED / FAILED
│           ├── messaging/
│           │   ├── MessagingConstants.java       # Exchange/queue/key names
│           │   ├── OrderEventPublisher.java      # Publishes order.created events
│           │   ├── OrderStatusListener.java      # Consumes order.status.changed
│           │   ├── RabbitConfig.java
│           │   └── event/
│           │       ├── OrderCreatedEvent.java
│           │       └── OrderStatusChangedEvent.java
│           ├── repository/
│           │   └── OrderRepository.java
│           └── service/
│               └── OrderService.java
│
├── saga-orchestrator/          # SAGA pattern coordinator
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       └── main/java/.../sagaorchestrator/
│           ├── SagaOrchestratorApplication.java
│           ├── domain/
│           │   ├── SagaInstance.java             # JPA entity: one row per order saga
│           │   ├── SagaState.java               # RUNNING → COMPLETED / COMPENSATED / FAILED
│           │   ├── SagaStep.java                # JPA entity: one row per step attempt
│           │   ├── SagaStepType.java            # BILLING → STOCK_RESERVATION → ROUTE_PLANNING
│           │   └── StepStatus.java
│           ├── messaging/
│           │   ├── MessagingConstants.java
│           │   ├── RabbitConfig.java
│           │   ├── SagaEventListener.java        # Listens for order.created & step results
│           │   ├── SagaMessenger.java             # Sends commands & status updates
│           │   ├── command/
│           │   │   └── StepCommand.java
│           │   └── event/
│           │       ├── OrderCreatedEvent.java
│           │       ├── OrderStatusChangedEvent.java
│           │       └── StepResult.java
│           ├── orchestration/
│           │   ├── OrderSagaOrchestrator.java     # The core saga state machine
│           │   └── SagaTimeoutMonitor.java        # Detects stuck steps
│           └── repository/
│               └── SagaInstanceRepository.java
│
├── adapters/
│   ├── cms-soap-adapter/       # Translates RabbitMQ commands → SOAP/XML calls
│   │   ├── Dockerfile
│   │   ├── pom.xml             # Includes JAXB code generation from cms.wsdl
│   │   └── src/
│   │
│   ├── wms-tcp-adapter/        # Translates RabbitMQ commands → raw TCP (24-byte protocol)
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/
│   │
│   └── ros-rest-adapter/       # Translates RabbitMQ commands → REST/JSON calls
│       ├── Dockerfile
│       ├── pom.xml
│       └── src/
│
├── mock-legacy-cms/            # Fake CMS: SOAP/XML service (Node.js + node-soap)
│   ├── Dockerfile
│   ├── server.js
│   ├── cms.wsdl                # WSDL contract
│   └── package.json
│
├── legacy-wms-daemon/          # Fake WMS: raw TCP server (C++ 17)
│   ├── Dockerfile
│   ├── CMakeLists.txt
│   ├── include/
│   │   └── wms_protocol.h      # 24-byte binary protocol definition
│   └── src/
│       └── wms_server.cpp
│
├── mock-legacy-ros/            # Fake ROS: REST/JSON service (Node.js + Express)
│   ├── Dockerfile
│   ├── server.js
│   ├── routing.js              # Nearest-neighbour route optimizer
│   └── package.json
│
└── frontend-portal/            # Client portal (Next.js 15)
    ├── Dockerfile
    ├── next.config.js           # standalone output for Docker
    ├── package.json
    ├── app/
    │   ├── globals.css
    │   ├── layout.js
    │   ├── page.js              # Login page
    │   └── orders/
    │       └── page.js          # Order submission & status tracking
    ├── components/
    │   └── OrderTracker.js      # Live 5-stage progress tracker
    └── lib/
        ├── api.js               # All fetch calls (login, submit order, poll status)
        └── orderStages.js       # Maps backend statuses to UI stages
```

---

## Services in Detail

### 1. Gateway Service

| | |
|---|---|
| **Port** | `8080` |
| **Framework** | Spring Cloud Gateway (reactive) |
| **Purpose** | Single entry point for all external traffic |

- Routes `/api/auth/**` → Auth Service (open, no token required)
- Routes `/api/orders/**` → Order Service (protected by JWT filter)
- Validates the JWT signature using the shared `JWT_SECRET`
- On valid token, strips the `Authorization` header and injects `X-Client-Id` and `X-User-Role` headers so downstream services never see the token
- Handles CORS to allow the frontend origin

### 2. Auth Service

| | |
|---|---|
| **Port** | `8086` |
| **Framework** | Spring Boot 3.5 |
| **Database** | `swifttrack_auth` (PostgreSQL) |
| **Purpose** | Credential verification and JWT issuance |

- Exposes `POST /api/auth/login` — exchanges `{ username, password }` for a signed JWT
- Passwords are hashed with BCrypt (using `spring-security-crypto`, not the full Spring Security starter)
- JWTs are signed with HMAC-SHA256 via JJWT library
- Seeds a default test user on startup (configurable via env vars)
- **Default credentials**: `acme-corp` / `swift1234`

### 3. Order Service

| | |
|---|---|
| **Port** | `8081` |
| **Framework** | Spring Boot 3.5 |
| **Database** | `swifttrack_orders` (PostgreSQL) |
| **Purpose** | Accept and track delivery orders |

- `POST /api/orders` — creates an order, publishes `order.created` to RabbitMQ
- `GET /api/orders/{orderId}` — returns the current status (polled by the frontend)
- `GET /api/orders?clientId=...` — lists orders for a client
- Listens on RabbitMQ for `order.status.changed` events from the orchestrator and updates the order status accordingly

### 4. SAGA Orchestrator

| | |
|---|---|
| **Port** | `8082` |
| **Framework** | Spring Boot 3.5 |
| **Database** | `swifttrack_saga` (PostgreSQL) |
| **Purpose** | Coordinates the 3-step saga and compensates on failure |

The orchestrator drives each order through three steps **in sequence**:

| Step | Legacy System | Routing Key | Compensation Key | Success Status |
|------|---------------|-------------|------------------|----------------|
| 1. **BILLING** | CMS | `cms.billing.create` | `cms.billing.cancel` | `BILLED` |
| 2. **STOCK_RESERVATION** | WMS | `wms.stock.reserve` | `wms.stock.release` | `STOCK_RESERVED` |
| 3. **ROUTE_PLANNING** | ROS | `ros.route.plan` | `ros.route.cancel` | `ROUTE_PLANNED` |

- If any step fails, previously completed steps are **compensated in reverse order**
- A timeout monitor detects steps that have been waiting too long (default: 30 seconds) and triggers compensation
- All state is persisted in PostgreSQL, so the saga survives restarts
- Idempotent: duplicate messages are detected and safely ignored

### 5. Adapters

Each adapter translates between the RabbitMQ-based messaging of the middleware and the native protocol of one legacy system:

| Adapter | Port | Protocol | Description |
|---------|------|----------|-------------|
| **CMS SOAP Adapter** | `8084` | SOAP/XML | Uses Spring WS `WebServiceTemplate` with JAXB-generated classes from `cms.wsdl` |
| **WMS TCP Adapter** | `8083` | Raw TCP | Writes a 24-byte binary frame (8-byte order ID + 16-byte command) over a TCP socket |
| **ROS REST Adapter** | `8085` | REST/JSON | Uses WebClient to call `POST /api/routes` and `DELETE /api/routes/:routeId` |

### 6. Mock Legacy Systems

These simulate the three legacy systems that would exist in a real deployment:

| Mock | Port | Protocol | Language | Description |
|------|------|----------|----------|-------------|
| **CMS Mock** | `3002` | SOAP/XML | Node.js (Express + node-soap) | Raises invoices via `SubmitOrder`, cancels them via `CancelBilling`. WSDL at `/cms?wsdl` |
| **WMS Mock** | `9090` | Raw TCP | C++ 17 | Accepts a 24-byte binary request over TCP, waits 1 second, returns `ACK_SUCCESS` or `ACK_FAILURE` |
| **ROS Mock** | `3001` | REST/JSON | Node.js (Express) | Plans delivery routes via `POST /api/routes`, cancels via `DELETE /api/routes/:routeId` |

All three support **forced failure mode** for testing compensation (see [Testing Failure Scenarios](#testing-failure-scenarios)).

### 7. Frontend Portal

| | |
|---|---|
| **Port** | `3000` |
| **Framework** | Next.js 15 (App Router), React 19 |
| **Purpose** | Client-facing web UI |

- **Login page** — authenticates via the gateway (`/api/auth/login`)
- **Orders page** — submit new orders, view all previous orders
- **Live status tracker** — polls order status every few seconds and shows a 5-stage progress bar:
  1. Order received
  2. CMS confirmed (billing)
  3. Warehouse allocated (stock reservation)
  4. Route optimized (route planning)
  5. Complete (ready for delivery)
- Token is stored in `sessionStorage` and attached as `Authorization: Bearer <token>` on all subsequent calls

---

## Infrastructure

| Component | Image | Port (Host) | Port (Container) | Purpose |
|-----------|-------|-------------|-------------------|---------|
| **RabbitMQ** | `rabbitmq:3.13-management-alpine` | `5672`, `15672` | `5672`, `15672` | Message broker. Management UI at [http://localhost:15672](http://localhost:15672) |
| **PostgreSQL** | `postgres:16-alpine` | `5433` (configurable) | `5432` | Three databases: `swifttrack_orders`, `swifttrack_saga`, `swifttrack_auth` |

- The PostgreSQL port is published on **5433** by default (to avoid clashing with a local Postgres). Override with `DB_PORT=5432` in a `.env` file.
- Named Docker volumes (`postgres-data`, `rabbitmq-data`) persist data across restarts.
- A single private bridge network (`swifttrack`) allows all services to reach each other by service name.

---

## Prerequisites

- **Docker** and **Docker Compose** (v2+)

That's all you need for the quick start. Docker builds everything inside containers.

If you want to run individual services outside Docker:

- **Java 21** (JDK, e.g. Eclipse Temurin)
- **Maven 3.9+** (or use the Maven Wrapper if added)
- **Node.js 20+** and **npm**
- **C++ compiler** with C++17 support and **CMake 3.16+** (only for the WMS daemon)
- **PostgreSQL 16** running locally
- **RabbitMQ 3.13** running locally

---

## Getting Started

### Quick Start (Docker Compose)

1. **Clone the repository**

   ```bash
   git clone https://github.com/UmeshMadhusankha/swifttrack-middleware.git
   cd swifttrack-middleware
   ```

2. **Start the full stack**

   ```bash
   docker compose up --build
   ```

   This builds and starts all 11 services. First build takes a few minutes (Maven downloads, npm installs, C++ compilation).

3. **Wait for all services to become healthy**

   Watch the logs or check with:

   ```bash
   docker compose ps
   ```

   All containers should show `healthy` status. The startup order is managed via `depends_on` with health checks.

4. **Open the frontend**

   Navigate to [http://localhost:3000](http://localhost:3000) in your browser.

5. **Log in with the default credentials**

   | Username | Password |
   |----------|----------|
   | `acme-corp` | `swift1234` |

6. **Submit an order and watch it progress through all three legacy systems!**

To stop:

```bash
docker compose down           # stop and remove containers
docker compose down -v        # also remove data volumes (fresh start)
```

### Running Services Individually

If you want to run services outside Docker for development:

1. **Start infrastructure first**

   ```bash
   # Start only RabbitMQ and PostgreSQL
   docker compose up rabbitmq postgres -d
   ```

2. **Run a Java service** (e.g., order-service)

   ```bash
   cd order-service
   ./mvnw spring-boot:run
   # or: mvn spring-boot:run
   ```

   Each service's `application.yml` defaults to `localhost` for its database and RabbitMQ connections.

3. **Run a Node.js mock** (e.g., mock-legacy-ros)

   ```bash
   cd mock-legacy-ros
   npm install
   npm run dev    # uses --watch for auto-reload
   ```

4. **Run the frontend**

   ```bash
   cd frontend-portal
   npm install
   npm run dev
   ```

   Opens at [http://localhost:3000](http://localhost:3000). Defaults to calling the gateway at `http://localhost:8080`.

> **Note:** When running outside Docker, the Postgres port is `5433` on the host. The auth-service `application.yml` already accounts for this. Other services default to port `5432`, which matches the in-container port. If running locally, adjust `DB_URL` or `DB_PORT` as needed.

---

## Environment Variables

All variables have sensible defaults for local development. Override them in a `.env` file at the project root (git-ignored) or inline with `docker compose`.

### Global / Shared

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_USERNAME` | `swifttrack` | PostgreSQL username |
| `DB_PASSWORD` | `swifttrack` | PostgreSQL password |
| `DB_PORT` | `5433` | Host port for PostgreSQL |
| `RABBITMQ_USER` | `guest` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `JWT_SECRET` | `swifttrack-local-development-secret-key-change-me` | Shared HMAC key (must match between auth-service and gateway) |
| `JWT_EXPIRY_MINUTES` | `60` | Token TTL |

### Auth Service

| Variable | Default | Description |
|----------|---------|-------------|
| `SEED_USERNAME` | `acme-corp` | Username of the auto-created test account |
| `SEED_PASSWORD` | `swift1234` | Password of the auto-created test account |

### SAGA Orchestrator

| Variable | Default | Description |
|----------|---------|-------------|
| `SAGA_STEP_TIMEOUT_SECONDS` | `30` | How long a step can go unanswered before it's treated as failed |
| `SAGA_TIMEOUT_CHECK_INTERVAL_MS` | `5000` | How often the timeout monitor scans for stuck steps |

### Gateway Service

| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_SERVICE_URL` | `http://auth-service:8086` | Where to forward auth requests |
| `ORDER_SERVICE_URL` | `http://order-service:8081` | Where to forward order requests |
| `ALLOWED_ORIGINS` | `http://localhost:3000` | CORS allowed origins for the browser |

### Frontend Portal

| Variable | Default | Description |
|----------|---------|-------------|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Gateway address as seen by the browser (baked in at build time) |

### Forced Failure Flags (Mock Legacy Systems)

| Variable | Default | Description |
|----------|---------|-------------|
| `WMS_FORCE_FAILURE` | `false` | Make every WMS request fail |
| `ROS_FORCE_FAILURE` | `false` | Make every ROS route request fail |
| `CMS_FORCE_FAILURE` | `false` | Make every CMS `SubmitOrder` fail |
| `CMS_FORCE_CANCEL_FAILURE` | `false` | Make CMS `CancelBilling` (compensation) fail |

---

## API Reference

All API calls go through the gateway at `http://localhost:8080`.

### Authentication

#### `POST /api/auth/login`

No token required.

**Request:**
```json
{
  "username": "acme-corp",
  "password": "swift1234"
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "username": "acme-corp",
  "role": "CLIENT"
}
```

**Response (401):**
```json
{
  "error": "INVALID_CREDENTIALS",
  "message": "Wrong username or password"
}
```

### Orders

All order endpoints require `Authorization: Bearer <token>`.

#### `POST /api/orders`

**Request:**
```json
{
  "recipientName": "Jane Doe",
  "deliveryAddress": "42 Elm Street, Springfield",
  "packageDescription": "2× fragile electronics"
}
```

**Response (201):**
```json
{
  "id": 1,
  "clientId": "acme-corp",
  "recipientName": "Jane Doe",
  "deliveryAddress": "42 Elm Street, Springfield",
  "packageDescription": "2× fragile electronics",
  "status": "PENDING",
  "statusDetail": null,
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

#### `GET /api/orders/{orderId}`

**Response (200):**
```json
{
  "id": 1,
  "clientId": "acme-corp",
  "recipientName": "Jane Doe",
  "deliveryAddress": "42 Elm Street, Springfield",
  "packageDescription": "2× fragile electronics",
  "status": "COMPLETED",
  "statusDetail": "Order is ready for delivery",
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:05Z"
}
```

#### `GET /api/orders?clientId=acme-corp`

Returns an array of `OrderResponse` objects.

---

## Message Flow (RabbitMQ)

The middleware uses two topic exchanges:

| Exchange | Purpose |
|----------|---------|
| `swifttrack.events` | Broadcast announcements (any service may listen) |
| `swifttrack.commands` | Directed instructions to specific adapters |

### Happy Path Message Flow

```
Order Service                    SAGA Orchestrator                    Adapters
     │                                │                                  │
     │──order.created───────────────►│                                  │
     │                                │──cms.billing.create────────────►│ CMS
     │                                │◄──saga.step.BILLING────────────│
     │◄──order.status.changed (BILLED)│                                │
     │                                │──wms.stock.reserve─────────────►│ WMS
     │                                │◄──saga.step.STOCK_RESERVATION──│
     │◄──order.status.changed─────────│                                │
     │    (STOCK_RESERVED)            │──ros.route.plan────────────────►│ ROS
     │                                │◄──saga.step.ROUTE_PLANNING─────│
     │◄──order.status.changed─────────│                                │
     │    (COMPLETED)                 │                                │
```

### Compensation Flow (on failure)

If step N fails, steps N-1 through 1 are undone in reverse order via compensation routing keys (e.g., `cms.billing.cancel`, `wms.stock.release`, `ros.route.cancel`).

---

## Order Lifecycle

```
PENDING ──► PROCESSING ──► BILLED ──► STOCK_RESERVED ──► ROUTE_PLANNED ──► COMPLETED
                │              │              │
                └──────────────┴──────────────┴──► COMPENSATING ──► FAILED
```

| Status | Meaning |
|--------|---------|
| `PENDING` | Saved in the database, not yet picked up by the orchestrator |
| `PROCESSING` | Orchestrator has started the saga |
| `BILLED` | CMS accepted the billing record |
| `STOCK_RESERVED` | WMS reserved warehouse stock |
| `ROUTE_PLANNED` | ROS produced a delivery route |
| `COMPLETED` | All three legacy systems succeeded |
| `COMPENSATING` | A step failed; undoing previously completed steps |
| `FAILED` | Order could not be fulfilled; all completed work has been undone |

---

## Database Schema

Three separate PostgreSQL databases enforce isolation between services:

| Database | Service | Tables |
|----------|---------|--------|
| `swifttrack_orders` | Order Service | `orders` — delivery orders with their current status |
| `swifttrack_saga` | SAGA Orchestrator | `saga_instance` — one row per order saga; `saga_step` — one row per step attempt |
| `swifttrack_auth` | Auth Service | `app_user` — user credentials (BCrypt hashed) |

Tables are auto-created/updated by Hibernate on startup (`ddl-auto: update`).

---

## Testing Failure Scenarios

### Via Environment Variables

Set failure flags in `.env` or `docker-compose` override:

```bash
# Make the WMS step fail (triggers compensation of CMS billing)
WMS_FORCE_FAILURE=true docker compose up

# Make the ROS step fail (triggers compensation of both WMS and CMS)
ROS_FORCE_FAILURE=true docker compose up

# Make CMS fail on the first step (no compensation needed)
CMS_FORCE_FAILURE=true docker compose up

# Make CMS compensation fail (leaves the saga in FAILED state with inconsistency)
CMS_FORCE_CANCEL_FAILURE=true docker compose up
```

### Via Runtime API (no restart needed)

**Toggle ROS failure:**

```bash
curl -X POST http://localhost:3001/control/force-failure \
  -H 'Content-Type: application/json' \
  -d '{"enabled": true}'
```

**Toggle CMS failure:**

```bash
curl -X POST http://localhost:3002/control/force-failure \
  -H 'Content-Type: application/json' \
  -d '{"submitOrder": true, "cancelBilling": false}'
```

**Check CMS invoices** (verify compensation ran):

```bash
curl http://localhost:3002/control/invoices
```

---

## Useful URLs

| URL | Description |
|-----|-------------|
| [http://localhost:3000](http://localhost:3000) | Frontend Portal (login & order management) |
| [http://localhost:8080](http://localhost:8080) | API Gateway |
| [http://localhost:15672](http://localhost:15672) | RabbitMQ Management UI (`guest` / `guest`) |
| [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health) | Order Service health |
| [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health) | SAGA Orchestrator health |
| [http://localhost:8083/actuator/health](http://localhost:8083/actuator/health) | WMS Adapter health |
| [http://localhost:8084/actuator/health](http://localhost:8084/actuator/health) | CMS Adapter health |
| [http://localhost:8085/actuator/health](http://localhost:8085/actuator/health) | ROS Adapter health |
| [http://localhost:8086/actuator/health](http://localhost:8086/actuator/health) | Auth Service health |
| [http://localhost:3001/health](http://localhost:3001/health) | Mock ROS health |
| [http://localhost:3002/health](http://localhost:3002/health) | Mock CMS health |
| [http://localhost:3002/cms?wsdl](http://localhost:3002/cms?wsdl) | CMS WSDL contract |

---

## Troubleshooting

### Services failing health checks on startup

The first build downloads Maven dependencies and npm packages, which can take a while. Docker health checks may time out. Simply wait and re-run:

```bash
docker compose down
docker compose up --build
```

### Port conflicts

- **5432 busy**: Postgres publishes on `5433` by default. If you need `5432`, set `DB_PORT=5432` in `.env`.
- **8080 busy**: Another service uses the gateway port. Stop it or change the gateway port in `docker-compose.yml`.
- **3000 busy**: Another dev server is running. Stop it or change the frontend port.

### "Connection refused" from a service inside Docker

Services use Docker service names (e.g., `rabbitmq`, `postgres`, `cms-mock`) to reach each other — not `localhost`. If a service reports connection failures, check that its environment variables use service names, not `localhost`.

### RabbitMQ not ready

Java services depend on RabbitMQ being healthy, but the connection may fail if RabbitMQ is still initializing. Spring will retry automatically. Check the logs:

```bash
docker compose logs -f rabbitmq
docker compose logs -f order-service
```

### Frontend shows "Network Error"

- Ensure the gateway is running and healthy
- Check that `NEXT_PUBLIC_API_BASE_URL` is `http://localhost:8080` (this is baked at build time in Docker)
- Check the browser console for CORS errors — the gateway's `ALLOWED_ORIGINS` must include the frontend's origin

### Database needs a fresh start

```bash
docker compose down -v    # Removes data volumes
docker compose up --build
```

This drops all data and re-runs `postgres-init.sql` to recreate the three databases.

### Viewing detailed logs

```bash
docker compose logs -f saga-orchestrator     # Follow orchestrator logs
docker compose logs -f cms-adapter           # Follow CMS adapter logs
docker compose logs --tail=100 order-service  # Last 100 lines
```

All middleware Java services log at `DEBUG` level for the `com.swiftlogistics` package.
