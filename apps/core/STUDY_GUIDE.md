# Iron Will Core: The Comprehensive Developer's Handbook & Study Guide

**Target Audience**: Mid-level engineers learning Spring Boot & enterprise backend patterns.  
**Goal**: Explain end-to-end what’s implemented in Core today so a new teammate can reason about it, change it safely, and extend it.

---

## Table of Contents
1. [Chapter 1: Architectural Philosophy & The "Why"](#chapter-1-architectural-philosophy--the-why)  
2. [Chapter 2: Project Autopsy (Structure & Dependencies)](#chapter-2-project-autopsy-structure--dependencies)  
3. [Chapter 3: Spring Boot Mechanics (Configuration & DI)](#chapter-3-spring-boot-mechanics-configuration--di)  
4. [Chapter 4: The Database Layer (JPA & Entities)](#chapter-4-the-database-layer-jpa--entities)  
5. [Chapter 5: Security & Auth Deep Dive (Beginner Friendly)](#chapter-5-security--auth-deep-dive-beginner-friendly)  
6. [Chapter 6: Core Business Logic (Services)](#chapter-6-core-business-logic-services)  
7. [Chapter 7: The API Layer (Controllers)](#chapter-7-the-api-layer-controllers)  
8. [Chapter 8: External Integrations (AI Agent & Storage)](#chapter-8-external-integrations-ai-agent--storage)  
9. [Chapter 9: The Scheduler & Background Tasks](#chapter-9-the-scheduler--background-tasks)  
10. [Chapter 10: Testing Strategy](#chapter-10-testing-strategy)  
11. [Chapter 11: Implementation Map (Where to Look)](#chapter-11-implementation-map-where-to-look)

---

## Chapter 1: Architectural Philosophy & The "Why"

### 1.1 Monolith for now
* **Why**: Strong transactional boundaries (audit + score + lockout in one transaction) and operational simplicity on a small team; avoids distributed transaction complexity.

### 1.2 State Management & Score
* **Score**: `BigDecimal(4,2)` to avoid float drift near the 3.0 lockout threshold.
* **Lockout rule**: score < 3.0 ⇒ all ACTIVE goals → LOCKED for 24h; UI should become read-only/redirect to lockout.

### 1.3 Security posture
* **Stateless**: JWT; no server sessions. Any instance can validate tokens with the shared secret.
* **Defense-in-depth**: Upload validation (type/size), internal secret for Agent calls, CORS allowlist.

---

## Chapter 2: Project Autopsy (Structure & Dependencies)

### 2.1 Build (`build.gradle.kts`)
Key deps (what/why):
* `spring-boot-starter-web`: REST + JSON + embedded server.
* `spring-boot-starter-data-jpa`: ORM over Postgres.
* `spring-boot-starter-oauth2-client`: Google OAuth2 flow.
* `spring-cloud-gcp-starter-storage`: GCS client for proofs.
* `jjwt`: JWT issuing/validation.

### 2.2 Package structure (`com.ironwill.core`)
Layered by responsibility:
* `api/`: Controllers (HTTP front door).
* `service/`: Business logic.
* `repository/`: Data access.
* `model/`: JPA entities.
* `security/`: Auth filters/config.
* `config/`: Wiring, CORS, security.

---

## Chapter 3: Spring Boot Mechanics (Configuration & DI)

* `@SpringBootApplication` in `CoreApplication` bootstraps auto-config and component scan.
* Beans of note:
  - `SecurityConfig`: security filter chain, JWT filter, OAuth2 login wiring.
  - `CorsConfig`: allowed origins from env.
  - `PasswordEncoder`: BCrypt.
  - `AuthenticationManager`: exposed for manual auth in `/auth/login`.

---

## Chapter 4: The Database Layer (JPA & Entities)

Entities (implemented):
* `User`: email (unique), fullName, timezone, accountabilityScore (DECIMAL 4,2, default 5.00), passwordHash, roles, timestamps.
* `Role`: ROLE_USER, ROLE_ADMIN (join via user_roles).
* `Goal`: title, reviewTime (UTC time component), frequencyType (DAILY), criteriaConfig JSONB, status (ACTIVE/LOCKED/ARCHIVED), lockedUntil, timestamps.
* `AuditLog`: goal FK, auditDate (unique with goal), proofUrl, status (PENDING/VERIFIED/REJECTED/MISSED), agentRemarks, scoreImpact, submittedAt.
* `Notification`: user FK, message, is_read, createdAt (index on user_id, is_read).

Why JSONB for criteria: flexible goal rules while keeping relational FK integrity (user/goal).
Why unique (goal_id, audit_date): DB-level guarantee of one audit per day per goal; prevents double-submit races.

---

## Chapter 5: Security & Auth Deep Dive (Beginner Friendly)

### 5.1 Concepts
* JWT = wristband; stateless; signed with `JWT_SECRET`.
* Google OAuth2 = external ID provider; on first login we auto-provision user with ROLE_USER.

### 5.2 Authentication flows
* Credentials: `POST /auth/login` → JWT.
* Google OAuth2: redirect to Google, callback handled by Spring; `CustomOAuth2UserService` ensures user exists; `OAuth2LoginSuccessHandler` issues JWT in JSON.

### 5.3 Authorization
* `JwtAuthenticationFilter` extracts/validates Bearer token, loads user, stamps SecurityContext.
* `/auth/me` returns profile DTO: email, fullName, timezone, accountabilityScore, lockout flag, lockedUntil, roles.
* Stateless: no sessions; tokens validated per request.

---

## Chapter 6: Core Business Logic (Services)

### 6.1 `ScoreService`
* Deltas: +0.5 pass, -0.2 fail; missed (-1.0) reserved for scheduler.
* Threshold: score < 3.0 → lock all ACTIVE goals for 24h (sets lockedUntil).
* Lock threshold exposed to clients via goal responses.

### 6.2 `GoalService`
* Transactional list/create/update for user-owned goals; optional status filter.

### 6.3 `StorageService`
* Uploads proofs to GCS; hashed key `users/{userId}/goals/{goalId}/{date}_{hash}` for organization + dedupe.

### 6.4 `NotificationService`
* Persists notifications; used by scheduler and read endpoints.

---

## Chapter 7: The API Layer (Controllers)

### 7.1 `AuditController` — `POST /api/goals/{id}/audit`
1) Auth + ownership + status + user score check.  
2) Validate file: JPG/PNG, <= 5MB.  
3) Upload to GCS (hashed key).  
4) Call Agent (`/internal/judge/audit`, 30s timeout, X-Internal-Secret). Agent failure → TECHNICAL_DIFFICULTY, no penalty.  
5) Persist audit_log; apply +0.5/-0.2; lockout if score < 3.0.  
6) Respond: verdict (PASS/FAIL/TECHNICAL_DIFFICULTY), remarks, extractedMetrics, scoreImpact.  
HTTP: 200 on handled outcomes; 423 if locked; 400 on validation.

### 7.2 `GoalController`
* `GET/POST/PUT /api/goals`, status filter. Responses include lockThreshold and lockedUntil.

### 7.3 `UserController`
* `PUT /api/user/timezone`.

### 7.4 `NotificationController`
* `GET /api/notifications/unread`
* `POST /api/notifications/{id}/read`
* `POST /api/notifications/read-all`

### 7.5 `AuthController`
* `POST /auth/login` (credentials → JWT)
* `POST /auth/me` (profile DTO)
* Google OAuth2 handled via Spring login success handler.

---

## Chapter 8: External Integrations (AI Agent & Storage)

### 8.1 Agent (`AgentClient`)
* Sync HTTP via `WebClient`, 30s timeout, `X-Internal-Secret`.
* Errors/timeouts → treated as technical difficulty (no score penalty).

### 8.2 Storage (GCS)
* Via `StorageService`; enforces content type/size upstream in controller.

---

## Chapter 9: The Scheduler & Background Tasks

### 9.1 `NagScheduler`
* Cron every 15m; TZ-aware; skips 23:00–06:00 local.
* If now > reviewTime and no audit today for a goal, insert notification “Pending audit for: <title>”.

---

## Chapter 10: Testing Strategy

* Unit: isolate services (e.g., ScoreService locking when score < 3.0).
* Integration: `@SpringBootTest` with test DB; create user → goal → submit audit; mock Agent.
* External mocking: replace AgentClient with stubbed PASS/FAIL to keep tests deterministic.

---

## Chapter 11: Implementation Map (Where to Look)
- Auth & Security: `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `CustomOAuth2UserService`, `OAuth2LoginSuccessHandler`, `AuthController`
- Domain/Repos: `model/*`, `repository/*`
- Audit: `AuditController`, `StorageService`, `AgentClient`, `ScoreService`
- Goals: `GoalController`, `GoalService`
- Notifications: `NotificationController`, `NotificationService`, `NagScheduler`
- User Timezone: `UserController`
- Config: `application.yml` (DB, GCS, agent secret, JWT secret, OAuth client, admin seed, upload max, CORS)

**End of Guide**. You now have the map and rationale to work end-to-end on Iron Will Core.

---

## Appendix: Theory Quick Reference (Why These Choices Work)

### Stateless Auth (JWT) vs Sessions
- Sessions store state server-side (JSESSIONID + cache/DB). JWT stores claims client-side, signed by a secret. Any instance can validate without shared session storage → better horizontal scaling and simpler ops. Downside: revocation is harder; mitigate with short TTLs and rotation if needed.

### OAuth2 (Google)
- Delegated auth: the app never handles user passwords. Authorization Code flow exchanges a short-lived code for tokens; identity is proven by Google. We mint our own JWT for app sessions to avoid round-trips to Google on every request.

### CSRF vs JWT
- CSRF exploits browsers auto-sending cookies. With Authorization headers (JWT), the browser doesn’t auto-send, so CSRF risk is minimal; we disable CSRF for stateless APIs. If you ever use cookies, revisit CSRF defenses.

### CORS
- Browser-enforced SOP blocks cross-origin AJAX. CORS explicitly allows trusted origins. This is not an auth mechanism—just a browser gate—so server-side auth/validation remains necessary.

### BigDecimal vs Floating Point
- IEEE 754 floats/doubles introduce rounding error (e.g., 0.1+0.2=0.30000000000000004). For financial/score logic near thresholds (lockout at 3.0), use BigDecimal to avoid accidental lockouts or missed triggers.

### Transactions & Consistency
- `@Transactional` ensures ACID: audit insert + score update + lockout are all-or-nothing. Avoids partial writes (e.g., audit saved, score not updated) that break trust and logic.

### JSONB vs Strict Columns
- Goals have flexible criteria; JSONB stores semi-structured rules while keeping relational FKs (user/goal). Tradeoff: validation and querying are looser than strict columns, but flexibility outweighs the cost for evolving criteria.

### Idempotency & Uniqueness
- Unique (goal_id, audit_date) enforces “one audit per day per goal” at the DB. Prevents double-submit races more reliably than in-memory checks.

### Lockout Mechanics
- A hard threshold (score < 3.0) flips all ACTIVE goals to LOCKED for 24h. Centralized in ScoreService so every score change triggers the check. UI should present read-only/lockout screen during this window.

### File Upload Validation
- Enforce type (JPG/PNG) and size (<=5MB) before processing. Reduces attack surface (no arbitrary binary) and protects bandwidth/storage. Hash-based naming dedupes content and organizes per user/goal.

### Sync Agent Call with Timeout
- Simplicity over queues for MVP. A 30s timeout prevents hanging requests. Fail-open (TECHNICAL_DIFFICULTY) avoids unfair penalties due to AI instability. Later, an async/retry model could improve UX under load.

### Scheduler “Nag” Design
- Runs every 15m, TZ-aware, skips night hours (23:00–06:00) to respect users. Checks “past review_time and no audit today” before inserting notifications to avoid spam. Polling (60s) on frontend is simple and reliable; can evolve to push/SSE later.
# Iron Will Core: The Comprehensive Developer's Handbook & Study Guide

**Target Audience**: Mid-level Engineers learning Spring Boot & Enterprise Architecture.
**Goal**: To provide an end-to-end, line-by-line understanding of the Iron Will Core backend.

---

## Table of Contents
1.  [Chapter 1: Architectural Philosophy & The "Why"](#chapter-1-architectural-philosophy--the-why)
2.  [Chapter 2: Project Autopsy (Structure & Dependencies)](#chapter-2-project-autopsy-structure--dependencies)
3.  [Chapter 3: Spring Boot Mechanics (Configuration & DI)](#chapter-3-spring-boot-mechanics-configuration--di)
4.  [Chapter 4: The Database Layer (JPA & Entities)](#chapter-4-the-database-layer-jpa--entities)
5.  [Chapter 5: Security & Auth Deep Dive (Beginner Friendly)](#chapter-5-security--auth-deep-dive-beginner-friendly)
6.  [Chapter 6: Core Business Logic (Services)](#chapter-6-core-business-logic-services)
7.  [Chapter 7: The API Layer (Controllers)](#chapter-7-the-api-layer-controllers)
8.  [Chapter 8: External Integrations (AI Agent & Storage)](#chapter-8-external-integrations-ai-agent--storage)
9.  [Chapter 9: The Scheduler & Background Tasks](#chapter-9-the-scheduler--background-tasks)
10. [Chapter 10: Testing Strategy](#chapter-10-testing-strategy)

---

## Chapter 1: Architectural Philosophy & The "Why"

Before we look at code, we must understand the engineering constraints and decisions that shaped this system.

### 1.1 The Monolithic Choice
In an era of microservices, we chose a Monolith. **Why?**
*   **Transactional Integrity**: The most critical operation in Iron Will is "Audit Proof -> Update Score". If the audit is recorded but the score update fails, the user loses trust. In a monolith, we wrap this in a single `@Transactional` block. If any part fails, the database rolls back everything. In microservices, this would require complex distributed transactions (Sagas).
*   **Operational Simplicity**: We deploy one container. We monitor one process. For a team of <10 engineers, the overhead of managing 15 microservices (service mesh, discovery, distributed tracing) is a productivity killer.

### 1.2 State Management & The "Score"
The `accountabilityScore` is the heart of the system.
*   **Design Choice**: We use `BigDecimal` with a scale of 2 (e.g., `5.00`).
*   **Why?**: Floating point math (IEEE 754) is imprecise. `0.1 + 0.2` in Java double is `0.30000000000000004`. If a user is at `3.00` and loses `0.1`, a float might drop them to `2.999999`, triggering a lockout incorrectly. `BigDecimal` avoids this class of bug entirely.

### 1.3 Security Posture
*   **Statelessness**: We use JWTs (JSON Web Tokens). The server remembers nothing about the user's session.
*   **Why?**: This allows us to scale horizontally. If we add 10 more servers, any server can validate the token using the secret key. We don't need a central Redis session store.

---

## Chapter 2: Project Autopsy (Structure & Dependencies)

Let's dissect the project skeleton.

### 2.1 The Build File (`pom.xml` / `build.gradle.kts`)
The dependencies tell the story of the capabilities.

*   `spring-boot-starter-web`:
    *   **What it does**: Brings in Tomcat (embedded web server), Spring MVC (REST framework), and Jackson (JSON parser).
    *   **Why**: We are building a REST API.
*   `spring-boot-starter-data-jpa`:
    *   **What it does**: Hibernate (ORM) + Spring Data (Repository abstraction).
    *   **Why**: To interact with PostgreSQL using Java Objects (Entities) instead of raw SQL strings.
*   `spring-boot-starter-oauth2-client`:
    *   **What it does**: Handles the complex "Dance" of OAuth2 (Redirect to Google -> Get Code -> Exchange for Token -> Get User Info).
    *   **Why**: We don't want to handle passwords.
*   `spring-cloud-gcp-starter-storage`:
    *   **What it does**: Provides an idiomatic Java client for Google Cloud Storage.
    *   **Why**: We need to store user uploads (images) reliably.

### 2.2 Package Structure (`com.ironwill.core`)
The code is organized by **Layer** (technical responsibility), not by **Feature**.

*   `api/`: The "Front Door". Contains Controllers. Validates inputs, returns JSON.
*   `service/`: The "Brain". Contains Business Logic. Doesn't know about HTTP or JSON.
*   `repository/`: The "Library". interfaces for database access.
*   `model/`: The "Vocabulary". JPA Entities representing Database Tables.
*   `security/`: The "Bouncer". Auth filters and configurations.
*   `config/`: The "wiring". Spring Bean definitions.

---

## Chapter 3: Spring Boot Mechanics (Configuration & DI)

Spring Boot is an "Inversion of Control" (IoC) container. You don't create objects (`new Service()`); Spring creates them for you.

### 3.1 The Entry Point: `CoreApplication.java`
Annotated with `@SpringBootApplication`. This does three things:
1.  `@Configuration`: Marks it as a source of bean definitions.
2.  `@EnableAutoConfiguration`: Tells Spring to guess configuration based on your jar dependencies (e.g., "I see Postgres driver, I'll configure a DataSource").
3.  `@ComponentScan`: Tells Spring to scan the `com.ironwill.core` package for other components.

### 3.2 Configuration Classes (`config/`)

#### `CorsConfig.java`
**The Problem**: Browsers block AJAX requests from one domain (e.g., `localhost:3000`) to another (`localhost:8080`) by default.
**The Solution**: We define a `WebMvcConfigurer` bean.
**The Code**:
```java
registry.addMapping("/**") // Apply to all endpoints
        .allowedOrigins(allowedOrigins) // Only allow trusted domains (from env vars)
        .allowedMethods("GET", "POST", ...);
```
**Why**: Security. We don't want random malicious sites calling our API with the user's cookies (if we used them).

#### `SecurityConfig.java`
**The Problem**: Who is allowed to access what?
**The Solution**: The `SecurityFilterChain` bean.
**Key Decisions**:
*   `csrf.disable()`: CSRF attacks rely on browser cookies sending session IDs automatically. Since we use JWT headers (`Authorization: Bearer`), the browser doesn't send credentials automatically, so CSRF is irrelevant. Disabling it simplifies the API.
*   `sessionCreationPolicy(STATELESS)`: Tells Spring Security "Do not create a JSESSIONID cookie".

---

## Chapter 4: The Database Layer (JPA & Entities)

We use **JPA (Java Persistence API)** to map Java classes to PostgreSQL tables.

### 4.1 The `User` Entity
```java
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue private UUID id;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal accountabilityScore = BigDecimal.valueOf(5.00);
}
```
**Analysis**:
*   `@Id @GeneratedValue`: We rely on Hibernate to generate the UUID.
*   `precision = 4, scale = 2`: Maps to SQL `NUMERIC(4,2)`. This allows numbers up to `99.99`. This is a business constraint; scores shouldn't exceed this.

### 4.2 The `Goal` Entity & JSONB
```java
@Column(columnDefinition = "jsonb", nullable = false)
private JsonNode criteriaConfig;
```
**The Why**: This is a hybrid SQL/NoSQL approach.
*   **The Conflict**: We have strict relationships (User -> Goal), but flexible attributes (different goals have different rules).
*   **The Resolution**: We use Postgres `JSONB`. It allows us to store unstructured data for the goal logic (e.g., `{"pages": 10}` vs `{"duration": "30m"}`) while keeping the strict `user_id` foreign key relationship.

### 4.3 `AuditLog` & Unique Constraints
```java
@Table(uniqueConstraints = {
    @UniqueConstraint(name = "uq_goal_date", columnNames = {"goal_id", "audit_date"})
})
```
**The Why**: **Concurrency Control**.
*   **Scenario**: A user clicks "Submit" twice rapidly. Two requests hit the server.
*   **Defense**: The Database is the final source of truth. The second insert will fail with a `DataIntegrityViolationException`. We catch this and tell the user "Already submitted". This is much more robust than checking `if (exists)` in Java code, which has race conditions.

---

## Chapter 5: Security & Auth Deep Dive (Beginner Friendly)

If you are new to backend engineering, Authentication (AuthN) and Authorization (AuthZ) can be confusing. Let's break it down using a **Nightclub Analogy**.

### 5.1 The Concept: The Club, The ID, and The Wristband

1.  **The Club**: The Iron Will Application. It has a public lobby (Login Page) and a VIP area (The Dashboard).
2.  **The Bouncer**: Our `JwtAuthenticationFilter`. He stands at the door of the VIP area.
3.  **Government ID**: Your Google Account. It proves who you are globally.
4.  **The Wristband**: The **JWT (JSON Web Token)**. The club gives this to you after checking your ID. It works *only* at this specific club.

**The Workflow**:
1.  You arrive at the club. You show your Government ID (Google Login).
2.  The club checks if you are on the guest list (Database). If not, they write your name down (Registration).
3.  The club gives you a **Wristband** (JWT).
4.  For the rest of the night, you don't show your ID. You just flash your **Wristband** to the Bouncer to get drinks or enter rooms.

### 5.2 Phase 1: Authentication (Getting the Wristband)

This is the "Login with Google" part. It happens via **OAuth2**.

**The Steps (End-to-End)**:

1.  **User Action**: User clicks "Login with Google" on the Frontend.
2.  **Redirect**: The browser is sent to `accounts.google.com`.
    *   *Note*: Iron Will is not involved here. You are talking directly to Google.
3.  **Consent**: User types their Gmail password and says "Yes, Iron Will can see my email".
4.  **The Callback**: Google redirects the browser back to *our* server: `/login/oauth2/code/google`.
    *   It brings a "Code" (like a temporary ticket).
5.  **Exchange**: Spring Boot (behind the scenes) takes that Code, calls Google's server, and says "Is this valid?". Google replies "Yes, this is `alice@gmail.com`".

**Code Spotlight: `CustomOAuth2UserService`**
*   **Role**: The "Guest List Manager".
*   **What it does**: It intercepts the moment Google says "This is Alice".
*   **Logic**:
    *   `findByEmail("alice@gmail.com")`
    *   *If User Exists*: Great, let her in.
    *   *If User Missing*: Create a new `User` row in the Postgres database. Set her score to 5.00.
    *   *Return*: A generic `OAuth2User` object.

**Code Spotlight: `OAuth2LoginSuccessHandler`**
*   **Role**: The "Wristband Dispenser".
*   **What it does**: It runs immediately after the Guest List Manager finishes.
*   **Logic**:
    1.  Get the email from the authentication data.
    2.  **Mint the Token**: Call `JwtService.generate(email)`. This creates a long string (the JWT).
    3.  **Respond**: Send the token back to the browser as JSON: `{"token": "eyJh..."}`.

### 5.3 Phase 2: Authorization (Using the Wristband)

Now the user has the token. They want to "Submit a Proof".

**The Steps (End-to-End)**:

1.  **User Action**: User uploads a photo.
2.  **The Request**: The Frontend sends a `POST /api/goals/123/audit`.
    *   **Crucial**: It adds a header: `Authorization: Bearer eyJh...` (This is the wristband).
3.  **The Bouncer (`JwtAuthenticationFilter`)**:
    *   This Code runs *before* the Controller.
    *   **Step A**: Look for the Header. No header? Kick them out (403 Forbidden).
    *   **Step B**: Check the signature. Is this *our* wristband? Or a fake one drawn with crayon? We verify it using our `JWT_SECRET`.
    *   **Step C**: Read the name. "Oh, this is Alice."
    *   **Step D**: Load Alice's details from the Database (`UserDetailsService`).
    *   **Step E**: Stamp her hand (`SecurityContextHolder.setAuthentication(...)`).
4.  **The Controller**:
    *   Now the request reaches `AuditController`.
    *   It doesn't check passwords. It just asks: `currentUserService.requireCurrentUser()`.
    *   Since the Bouncer stamped her hand, we know it's Alice.

### 5.4 Why do we do it this way? (The "Why")

*   **Why swap Google ID for JWT?**: We don't want to call Google every time you click a button (slow, expensive). Our JWT is fast and local.
*   **Why Stateless?**: Note that in Phase 2, the server didn't check a list of "Currently Logged In Users". It just checked the *mathematical signature* of the token. This means if Server A issues the token, Server B can validate it without talking to Server A. This is **Horizontal Scalability**.

---

## Chapter 6: Core Business Logic (Services)

This is where the "Iron Will" rules live.

### 6.1 `ScoreService`: The Gamification Engine
This service is the only place allowed to modify scores.
**Key Logic**:
```java
public void applyDelta(User user, BigDecimal delta) {
    user.setAccountabilityScore(current.add(delta));
    if (user.getAccountabilityScore() < 3.0) {
        lockAllActiveGoals(user);
    }
}
```
**The Why**:
*   **Centralization**: By forcing all updates through here, we ensure we *never* forget to check for Lockout.
*   **The Lockout**: This is the "stick". If you fail too much, the app becomes read-only (LOCKED status) for 24 hours. This enforces the psychological contract.

### 6.2 `GoalService`: CRUD
Standard Create/Read/Update/Delete operations.
**Transactional Boundaries**: Methods are annotated with `@Transactional`. This ensures that if we create a Goal but fail to link it to a User, the database rolls back. No "zombie" data.

### 6.3 `StorageService`: Handling Files
We don't store images in the DB (BLOBs are bad for DB performance). We store them in Google Cloud Storage (GCS).
**Filename Strategy**: `users/{userId}/goals/{goalId}/{date}_{hash}`.
*   **Privacy**: Organized by user ID for easy data deletion.
*   **Uniqueness**: The `hash` (SHA-256 of content) ensures that if a user uploads the same image twice, it overwrites cleanly rather than creating duplicates.

---

## Chapter 7: The API Layer (Controllers)

Controllers translate HTTP to Java.

### 7.1 `AuditController`
The most critical endpoint: `POST /api/goals/{id}/audit`.
**The Workflow**:
1.  **Input Validation**: Is the file an image? Is it < 5MB?
2.  **State Check**: Is the user currently Locked Out? (If so, reject immediately with 423 Locked).
3.  **Upload**: Call `StorageService` to put file in GCS.
4.  **Verification**: Call `AgentClient` (The AI).
5.  **Score Update**: Based on AI verdict (PASS/FAIL), call `ScoreService`.
6.  **Response**: Return the result to the user.

**Design Note**: We return specific HTTP codes.
*   `200 OK`: Success.
*   `423 Locked`: Business logic rejection.
*   `400 Bad Request`: Validation failure.

---

## Chapter 8: External Integrations (AI Agent & Storage)

### 8.1 The Agent Client (`AgentClient.java`)
We communicate with a separate Python/FastAPI service that runs the Computer Vision models.
**Communication Style**: Synchronous HTTP (`WebClient`).
**The Why**:
*   **Simplicity**: Using a Message Queue (RabbitMQ) is "better" for scale but adds massive infrastructure complexity. For MVP, a simple HTTP call is easier to debug.
*   **Timeout**: We set a hard 30-second timeout. If the AI hangs, we don't want to hang the User's connection forever.
*   **Fail-Open**: If the Agent fails (500 Error), we catch the exception and mark the audit as `PENDING`. We do *not* fail the user. This is a "Resiliency Pattern".

### 8.2 Security
We send a header `X-Internal-Secret`.
**The Why**: This is a shared secret between Core and Agent. It prevents random internet users from calling the Agent API directly.

---

## Chapter 9: The Scheduler & Background Tasks

### 9.1 `NagScheduler`
**Problem**: Users forget to submit proofs.
**Solution**: A cron job running every 15 minutes.
**The Logic**:
1.  Find all users.
2.  Calculate their *local* time (using `user.timezone`).
3.  If it is after their `reviewTime` AND they haven't submitted a proof today:
    *   Send a Notification.
**Optimization**: We check "Quiet Hours" (23:00 - 06:00). We don't spam users while they sleep. This is "Empathic Engineering".

---

## Chapter 10: Testing Strategy

How do we know it works?

### 10.1 Unit Tests
We use `Mockito` to test `ScoreService`.
*   *Test*: "If score drops below 3.0, verify `goalRepository.saveAll()` is called with LOCKED status."
*   *Why*: We test the logic in isolation from the database.

### 10.2 Integration Tests
We use `@SpringBootTest` with H2 (in-memory DB).
*   *Test*: "Create User -> Create Goal -> Submit Audit".
*   *Why*: Ensures the wiring between Controller, Service, and Repository works.

### 10.3 Mocking the External World
We never call the real AI Agent in tests. We mock `AgentClient` to return `AgentResponse(verdict="PASS")`.
**Why**: Tests must be deterministic and fast. Real network calls are flaky and slow.

---

## Appendix A: Deep Theoretical Foundations (Interview Prep)

This section provides university-level computer science theory behind every architectural decision. Master these concepts to ace system design interviews.

---

### A.1 Monolithic vs Microservices Architecture

#### Theory: Architectural Patterns

**Monolith**:
- **Definition**: Single deployable unit containing all business logic, data access, and API layers.
- **Process Model**: Runs as one OS process; components communicate via in-memory function calls.
- **Deployment**: Single artifact (JAR/WAR/container image).
- **Scaling**: Vertical (bigger machine) or horizontal (clone entire monolith behind load balancer).

**Microservices**:
- **Definition**: Application decomposed into small, independently deployable services, each owning a bounded context.
- **Process Model**: Each service runs in separate process/container; communicate via network (HTTP/gRPC/message queues).
- **Deployment**: Each service deployed independently (can use different languages/frameworks).
- **Scaling**: Granular (scale only the service under load).

#### Theoretical Tradeoffs

**Monolith Advantages**:
1. **Transactional Integrity**: Single database = ACID transactions. Audit submission in Iron Will requires atomicity: "record audit AND update score AND check lockout" must be all-or-nothing. In monolith, this is a single `@Transactional` method. In microservices, this requires distributed transactions (2PC or Sagas).
2. **Latency**: Function calls are nanoseconds; network calls are milliseconds (6 orders of magnitude difference). If "Submit Audit" requires calling 5 services, latency compounds.
3. **Debugging**: Single call stack. Logs in one place. No distributed tracing needed.
4. **Operational Simplicity**: One deployment pipeline, one monitoring dashboard, one health check.

**Microservices Advantages**:
1. **Independent Scaling**: If only the "Image Processing" service is under load, scale only that (not the entire app).
2. **Technology Diversity**: Use Python for ML, Go for high-throughput APIs, Java for transactions.
3. **Team Autonomy**: Each team owns a service end-to-end (database, API, deployment).
4. **Fault Isolation**: If the "Email Service" crashes, the rest of the app stays up.

**Microservices Disadvantages (Why We Avoided)**:
1. **Distributed Transactions Problem**: 
   - **Scenario**: AuditService records audit (commits to DB), calls ScoreService to update score. ScoreService fails.
   - **Problem**: Audit is recorded but score not updated (data inconsistency).
   - **Solutions**:
     - **2-Phase Commit (2PC)**: Coordinator asks all services "can you commit?", then "commit". Blocking protocol (slow, brittle).
     - **Saga Pattern**: Chain of local transactions with compensating actions. If ScoreService fails, AuditService must "undo" by deleting the audit. Complex to implement and reason about.
2. **CAP Theorem**: In distributed systems, you can only have 2 of 3: Consistency, Availability, Partition Tolerance. Network partitions happen (Murphy's Law), so you must choose: sacrifice consistency (eventual consistency) or availability (reject requests during partition). Monolith sidesteps this for internal operations.
3. **Service Mesh Overhead**: Need Istio/Linkerd for service discovery, load balancing, circuit breaking, retries, distributed tracing. Adds 20-50ms per hop.

**Our Decision**: Iron Will chose **Modular Monolith** (monolith with clean internal boundaries). We get transactional integrity and operational simplicity. When we scale, we horizontally replicate the entire monolith (stateless JWT makes this trivial). Only if a specific component (e.g., image processing) becomes a bottleneck do we extract it as a microservice.

---

### A.2 Transactional Integrity (ACID Properties)

#### Theory: Database Transactions

**ACID** is the gold standard for transactional systems. Let's break down each property:

**1. Atomicity**:
- **Definition**: A transaction is an indivisible unit of work. Either ALL operations succeed, or ALL are rolled back.
- **Example**: Audit submission involves:
  1. `INSERT INTO audit_logs ...`
  2. `UPDATE users SET accountability_score = ...`
  3. `UPDATE goals SET locked_until = ...` (if score < 3.0)
- **Atomicity Guarantee**: If step 3 fails (e.g., database crash), steps 1 and 2 are automatically undone. The database never ends up in a state where audit is recorded but score not updated.
- **Implementation**: Database maintains a **transaction log** (WAL - Write-Ahead Log). Before applying changes to disk, writes them to log. On crash, replays log to redo committed transactions and undo uncommitted ones.

**2. Consistency**:
- **Definition**: A transaction brings the database from one valid state to another. All constraints (foreign keys, unique indexes, check constraints) are enforced.
- **Example**: `audit_logs.goal_id` has foreign key to `goals.id`. If you try to insert an audit for non-existent goal, transaction fails. Database never enters inconsistent state.
- **Business Logic Consistency**: Application-level invariants (e.g., "score must be between 0 and 10") enforced by business logic before committing transaction.

**3. Isolation**:
- **Definition**: Concurrent transactions do not interfere with each other. Each transaction sees a consistent snapshot of data.
- **Isolation Levels** (from weakest to strongest):
  1. **Read Uncommitted**: Can see uncommitted changes from other transactions (dirty reads). Almost never used.
  2. **Read Committed** (Postgres default): Only see committed data. But if you read twice in same transaction, may see different values (non-repeatable reads).
  3. **Repeatable Read**: See same snapshot throughout transaction. Prevents non-repeatable reads, but phantom reads possible (new rows inserted by other transactions).
  4. **Serializable**: Strongest. Transactions appear to execute serially (one after another). No concurrency anomalies, but lowest throughput.
- **Implementation**: Databases use **locks** (pessimistic) or **MVCC (Multi-Version Concurrency Control)** (optimistic). Postgres uses MVCC: each transaction sees a snapshot based on transaction ID. No read locks needed (readers never block writers).

**4. Durability**:
- **Definition**: Once a transaction commits, changes are permanent (survive crashes, power loss).
- **Implementation**: Before returning "commit success" to client, database flushes transaction log to disk. Even if server crashes 1ms later, on restart, database replays log and reapplies committed transaction.
- **Tradeoff**: Disk flush is slow (5-10ms for HDD, 0.1ms for SSD). High-throughput systems batch commits (group commit) to amortize flush overhead.

#### Spring's `@Transactional`

**What it does**:
1. Before method executes: `BEGIN TRANSACTION`
2. Execute method
3. If method returns normally: `COMMIT`
4. If method throws exception: `ROLLBACK`

**Propagation Types**:
- `REQUIRED` (default): Join existing transaction if present, else start new one.
- `REQUIRES_NEW`: Always start new transaction (suspend current one).
- `NESTED`: Start nested transaction (savepoint).

**Example**:
```java
@Transactional
public void submitAudit(UUID goalId, MultipartFile proof) {
    auditLog.save(...);  // SQL: INSERT INTO audit_logs
    scoreService.applyDelta(user, delta);  // SQL: UPDATE users SET score = ...
    if (user.score < 3.0) {
        goalService.lockAll(user);  // SQL: UPDATE goals SET locked_until = ...
    }
    // If any line throws exception, all SQL rolled back
}
```

**Without `@Transactional`**: Each `save()` / `update()` would be auto-committed immediately (auto-commit mode). If `lockAll()` crashes, audit and score are already committed (inconsistency).

---

### A.3 Floating Point Representation (Why BigDecimal)

#### Theory: IEEE 754 Floating Point

**How Computers Store Decimals**:

**Integers** are exact: `int x = 5;` stores exactly `101` in binary.

**Decimals** cannot always be exact in binary. Consider `0.1` (decimal):
- **Decimal**: `0.1 = 1/10`
- **Binary**: `0.1` is a repeating fraction in binary: `0.0001100110011001100110011...` (infinite)
- **IEEE 754 Float**: Stores only 23 bits of precision, so truncates: `0.10000000149011612` (close but not exact).

**The Problem**:
```java
double score = 3.0;
score = score - 0.1;  // 2.9
score = score - 0.1;  // 2.8
score = score - 0.1;  // 2.7 (actually 2.6999999999999997)
if (score < 2.7) {
    lockUser();  // BUG: locks user incorrectly!
}
```

**Root Cause**: Binary can exactly represent `1/2`, `1/4`, `1/8` (negative powers of 2) but not `1/10`, `1/5`, `1/3`.

#### BigDecimal Solution

**How it works**:
- Stores number as **unscaled integer** + **scale**.
- Example: `5.00` stored as `unscaledValue = 500`, `scale = 2`.
- Arithmetic: `5.00 + 0.50` = `add(500, 50) = 550` → `5.50`.

**No Precision Loss**: All operations done in integer math (exact), then scaled.

**Tradeoff**: 4-10x slower than primitive `double` (software arithmetic vs hardware FPU). For financial/scoring systems, correctness >> speed.

**Why `precision = 4, scale = 2`**:
- Maps to SQL `NUMERIC(4, 2)`.
- Allows values `0.00` to `99.99`.
- 4 total digits, 2 after decimal.

---

### A.4 JWT (JSON Web Tokens) - Deep Dive

#### Theory: Stateless Authentication

**The Problem**: How does server know "this request is from Alice"?

**Traditional Solution (Session Cookies)**:
1. User logs in → Server generates random session ID → Stores `{session_id: "abc123", user: "alice"}` in Redis/memory.
2. Server sends `Set-Cookie: SESSIONID=abc123`.
3. Browser auto-sends cookie on every request.
4. Server looks up session ID in Redis → "Oh, this is Alice".

**Downsides**:
- **Stateful**: Server must remember session (consumes memory).
- **Scaling**: If Load Balancer sends request to Server 2, Server 2 must access shared Redis (network hop, single point of failure).
- **Revocation**: Easy (delete session from Redis).

**JWT Solution (Stateless)**:
1. User logs in → Server generates **signed token** containing user info.
2. Server sends token to client: `{"token": "eyJhbGc..."}`.
3. Client stores token (localStorage/memory) and sends in header: `Authorization: Bearer eyJhbGc...`.
4. Server **validates signature** → "This token was signed by me, and it says user is Alice".

**Advantages**:
- **Stateless**: Server stores nothing. Any server can validate any token (horizontal scale).
- **No Database Lookup**: User info in token (claims).

**Disadvantages**:
- **Cannot Revoke**: Once issued, token valid until expiry. If token stolen, attacker has access until expiry (set short expiry + refresh tokens to mitigate).
- **Size**: Cookies ~50 bytes; JWTs ~200-500 bytes (sent on every request).

#### JWT Structure

A JWT has 3 parts separated by dots: `HEADER.PAYLOAD.SIGNATURE`

**1. Header** (JSON, Base64-encoded):
```json
{
  "alg": "HS256",  // Algorithm: HMAC SHA-256
  "typ": "JWT"
}
```
→ Base64: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9`

**2. Payload** (JSON, Base64-encoded):
```json
{
  "sub": "alice@example.com",  // Subject (user ID)
  "iat": 1699564800,            // Issued At (Unix timestamp)
  "exp": 1699651200             // Expiry (Unix timestamp)
}
```
→ Base64: `eyJzdWIiOiJhbGljZUBleGFtcGxlLmNvbSIsImlhdCI6MTY5OTU2NDgwMCwiZXhwIjoxNjk5NjUxMjAwfQ`

**3. Signature**:
```
HMACSHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret_key
)
```
→ `4Hb7...` (binary hash, Base64-encoded)

**Full JWT**: `eyJhbGc...eyJzdWI...4Hb7...`

#### Validation Process

```java
String[] parts = token.split("\\.");
String header = parts[0];
String payload = parts[1];
String signature = parts[2];

// Re-compute signature
String expected = HMAC_SHA256(header + "." + payload, JWT_SECRET);

if (signature != expected) {
    throw new InvalidTokenException();  // Token tampered!
}

// Decode payload
Claims claims = base64Decode(payload);
if (claims.exp < now()) {
    throw new ExpiredTokenException();
}

String email = claims.sub;  // This is the user
```

**Why Signature Prevents Tampering**:
- Attacker modifies payload: `"sub": "admin@example.com"`.
- Attacker cannot re-compute valid signature (doesn't know `JWT_SECRET`).
- Server rejects token (signature mismatch).

**Security**: `JWT_SECRET` must be:
- **Long**: ≥256 bits (32 bytes) for HS256.
- **Random**: Use `SecureRandom`, not predictable string.
- **Secret**: Store in environment variable / Secret Manager, never in code.

---

### A.5 OAuth2 Protocol (Complete Specification)

#### Theory: Delegated Authorization

**The Problem**: Your app (Iron Will) needs to access user's Google Calendar. How?

**Bad Solution**: Ask user for Google password.
- **Risk**: Your app now has full access to user's Google account (can read emails, delete data).
- **Trust**: User must trust you won't abuse this.
- **Revocation**: User must change password to revoke access.

**OAuth2 Solution**: Google gives your app a **limited-scope token** (e.g., "read calendar only"). User never shares password with you.

#### OAuth2 Roles

1. **Resource Owner**: The user (Alice).
2. **Client**: Your app (Iron Will).
3. **Authorization Server**: Google's OAuth2 server (`accounts.google.com`).
4. **Resource Server**: Google's API server (`www.googleapis.com/calendar`).

#### Authorization Code Flow (What We Use)

**Step-by-Step**:

**1. User Clicks "Login with Google"**:
- **Client Action**: Redirect browser to:
  ```
  https://accounts.google.com/o/oauth2/auth?
    client_id=YOUR_CLIENT_ID
    &redirect_uri=https://ironwill.com/oauth/callback
    &scope=openid email profile
    &response_type=code
    &state=RANDOM_STRING
  ```
- **Parameters**:
  - `client_id`: Your app's ID (registered with Google).
  - `redirect_uri`: Where Google sends user back.
  - `scope`: What permissions you want (`email`, `profile`, `calendar`, etc.).
  - `response_type=code`: "Give me an authorization code" (not token directly).
  - `state`: Random string to prevent CSRF (Google echoes it back; you verify).

**2. User Logs In at Google**:
- **User Action**: Types Google password (NOT at your site, at `accounts.google.com`).
- **Consent Screen**: "Iron Will wants to access your email and profile. Allow?"

**3. Google Redirects Back with Code**:
- **Browser Redirect**:
  ```
  https://ironwill.com/oauth/callback?code=4/0AX4XfWh...&state=RANDOM_STRING
  ```
- **Security Check**: Verify `state` matches (prevents CSRF).

**4. Exchange Code for Token** (Server-Side):
- **Client Action**: Make POST request to Google (server-to-server, not via browser):
  ```
  POST https://oauth2.googleapis.com/token
  Content-Type: application/x-www-form-urlencoded

  code=4/0AX4XfWh...
  &client_id=YOUR_CLIENT_ID
  &client_secret=YOUR_CLIENT_SECRET
  &redirect_uri=https://ironwill.com/oauth/callback
  &grant_type=authorization_code
  ```
- **Why Server-Side**: `client_secret` must never be exposed to browser (JavaScript). Only backend server knows it.
- **Response**:
  ```json
  {
    "access_token": "ya29.a0AfH6...",
    "token_type": "Bearer",
    "expires_in": 3600,
    "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjI3YTQyM..."
  }
  ```

**5. Get User Info**:
- **Client Action**: Call Google API with `access_token`:
  ```
  GET https://www.googleapis.com/oauth2/v2/userinfo
  Authorization: Bearer ya29.a0AfH6...
  ```
- **Response**:
  ```json
  {
    "id": "1084784584893",
    "email": "alice@gmail.com",
    "name": "Alice Johnson",
    "picture": "https://..."
  }
  ```

**6. Issue Your Own JWT**:
- **Our Server**: Now that we know user is `alice@gmail.com`, generate our own JWT:
  ```java
  String jwt = jwtService.generate("alice@gmail.com");
  ```
- **Response to Frontend**:
  ```json
  {"token": "eyJhbGc..."}
  ```
- **Frontend**: Stores token, uses for all subsequent requests.

#### Why This Flow is Secure

**Two-Step Process (Code → Token)**:
- **Step 1 (Browser)**: Google sends **code** to browser. Code is useless without `client_secret`.
- **Step 2 (Server)**: Server exchanges code + secret for token.
- **Why**: If attacker steals code (e.g., via browser history), they cannot exchange it (no secret). If token sent directly to browser, attacker could intercept (token reuse attack).

**State Parameter (CSRF Protection)**:
- **Attack Scenario**: Attacker tricks user into visiting `https://ironwill.com/oauth/callback?code=ATTACKER_CODE`.
- **Defense**: Your server checks `state` matches random value you generated in Step 1. Attacker doesn't know this value.

**Scope Limitation**:
- You request only `email` and `profile`.
- Token cannot access user's Gmail inbox, Drive, etc. (principle of least privilege).

---

### A.6 CORS (Cross-Origin Resource Sharing) - Security Model

#### Theory: Same-Origin Policy (SOP)

**The Foundation of Web Security**:

**Origin** = `scheme + host + port`
- `https://ironwill.com:443` is origin.
- `https://ironwill.com:8080` is **different origin** (different port).
- `http://ironwill.com` is **different origin** (different scheme).

**Same-Origin Policy (SOP)**: JavaScript running on `https://evil.com` **cannot**:
- Read responses from `https://ironwill.com/api/user` (even if request succeeds).
- Access cookies from `ironwill.com`.

**Why**: Without SOP, malicious site could:
```javascript
// evil.com page
fetch('https://bank.com/transfer?to=attacker&amount=1000', {
  credentials: 'include'  // Send user's bank.com cookies
});
// Browser sends cookies; bank transfers money; evil.com reads response
```

**SOP Defense**: Browser blocks `evil.com` from reading `bank.com` response.

#### CORS: Relaxing SOP (When You Trust the Origin)

**Scenario**: Your frontend is `http://localhost:3000`. Backend is `http://localhost:8080`. **Different origins** (different ports).

**Problem**: SOP blocks AJAX requests:
```javascript
// Frontend at localhost:3000
fetch('http://localhost:8080/api/goals')
  .then(res => res.json())  // ❌ CORS error
```

**Solution**: Backend opts-in via **CORS headers**:
```java
response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
```

**Browser Behavior**:
1. **Preflight Request** (for non-simple requests like POST/PUT with custom headers):
   ```
   OPTIONS /api/goals
   Origin: http://localhost:3000
   Access-Control-Request-Method: POST
   Access-Control-Request-Headers: Authorization
   ```
2. **Backend Response**:
   ```
   Access-Control-Allow-Origin: http://localhost:3000
   Access-Control-Allow-Methods: POST, GET, PUT
   Access-Control-Allow-Headers: Authorization, Content-Type
   ```
3. **Browser**: "Backend allows this origin and method. Proceed."
4. **Actual Request**:
   ```
   POST /api/goals
   Origin: http://localhost:3000
   Authorization: Bearer ...
   ```

**Why Preflight**: Prevents legacy servers (pre-CORS era) from executing dangerous operations. If server doesn't respond to OPTIONS, browser blocks request.

#### CORS vs CSRF

**CORS**: Prevents **reading** responses from cross-origin requests.
- Attack Mitigation: Malicious site cannot steal user data.

**CSRF**: Prevents **unwanted actions** from cross-origin requests.
- Attack Scenario:
  ```html
  <!-- evil.com page -->
  <form action="https://bank.com/transfer" method="POST">
    <input name="to" value="attacker">
    <input name="amount" value="1000">
  </form>
  <script>document.forms[0].submit();</script>
  ```
- **How it works**: Browser auto-sends `bank.com` cookies (SOP doesn't block sends, only reads). Bank sees authenticated request, transfers money.
- **Defense**: CSRF token (form includes hidden token that evil.com doesn't know).

**Why JWT Doesn't Need CSRF Protection**:
- **Cookie-based auth**: Browser auto-sends cookies (CSRF risk).
- **JWT (Bearer token)**: JavaScript must explicitly add `Authorization` header. `evil.com` cannot read `localStorage` of `ironwill.com` (SOP prevents this). No auto-send = no CSRF risk.

---

### A.7 Spring Boot Internals (IoC/DI)

#### Theory: Inversion of Control (IoC)

**Traditional Programming (You Control Flow)**:
```java
public class AuditController {
    private AuditService auditService = new AuditService();  // You create dependency
    
    public void submit() {
        auditService.process();
    }
}
```

**Problem**:
- **Tight Coupling**: `AuditController` hardcoded to specific `AuditService` implementation.
- **Testing**: Cannot mock `AuditService` (cannot inject test double).
- **Configuration**: What if `AuditService` needs a database connection? Must pass through constructor chain.

**IoC (Framework Controls Flow)**:
```java
@RestController
public class AuditController {
    private final AuditService auditService;
    
    @Autowired  // Spring injects dependency
    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }
}
```

**Spring's Job**:
1. **Scan** classpath for `@Component`, `@Service`, `@Repository`, `@Controller`.
2. **Instantiate** beans (Spring-managed objects).
3. **Inject** dependencies (constructor/field/setter injection).
4. **Lifecycle Management**: Call `@PostConstruct`, handle `@PreDestroy`, etc.

#### Dependency Injection (DI) Patterns

**1. Constructor Injection** (Recommended):
```java
public class AuditService {
    private final GoalRepository goalRepository;
    
    public AuditService(GoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }
}
```
**Advantages**:
- **Immutability**: `final` field (thread-safe).
- **Required Dependencies**: Compiler enforces (cannot construct without dependency).
- **Testability**: Easy to mock in tests.

**2. Field Injection** (Avoid):
```java
@Autowired
private GoalRepository goalRepository;  // Spring uses reflection to set field
```
**Disadvantages**:
- Cannot be `final` (set after construction).
- Hard to test (need Spring context to inject).
- Hides dependencies (not clear from constructor).

#### Bean Scopes

**Singleton** (default): One instance per Spring context.
- **Use Case**: Stateless services (e.g., `AuditService`).
- **Thread Safety**: Must be thread-safe (no mutable instance variables).

**Prototype**: New instance every time bean requested.
- **Use Case**: Stateful objects (e.g., shopping cart).

**Request**: One instance per HTTP request (web apps only).

**Session**: One instance per HTTP session.

#### How Spring Starts Up

**1. Component Scan**:
- Spring scans `com.ironwill.core` package (and subpackages).
- Finds classes annotated with `@Component`, `@Service`, etc.
- Registers bean definitions.

**2. Dependency Resolution**:
- Builds dependency graph.
- Detects circular dependencies (A → B → A).
- Determines creation order.

**3. Bean Instantiation**:
- Creates beans in order (dependencies first).
- Injects dependencies via constructor/field/setter.

**4. Post-Processing**:
- Calls `@PostConstruct` methods.
- Applies AOP proxies (for `@Transactional`, `@Async`, etc.).

**5. Ready**:
- Application context fully initialized.
- `SpringApplication.run()` returns.

---

### A.8 JPA & Hibernate (ORM Theory)

#### Theory: Object-Relational Impedance Mismatch

**The Problem**: Object-oriented programming vs relational databases have fundamentally different models.

**1. Identity**:
- **Java**: Object identity (`==`) vs object equality (`.equals()`).
- **Database**: Primary key uniqueness.
- **Mismatch**: Same row loaded twice creates two Java objects (`obj1 != obj2`).
- **JPA Solution**: **Persistence Context** (first-level cache). Within transaction, same row always returns same object instance.

**2. Relationships**:
- **Java**: Directional references (`user.getGoals()`).
- **Database**: Bidirectional foreign keys (`goals.user_id`).
- **Mismatch**: Navigating relationship requires explicit query.
- **JPA Solution**: Lazy loading. `user.getGoals()` triggers SQL query on-demand.

**3. Inheritance**:
- **Java**: Class hierarchies (`Vehicle → Car / Truck`).
- **Database**: No native inheritance.
- **JPA Solution**: Inheritance strategies:
  - **Single Table**: One table with discriminator column.
  - **Joined**: One table per class, joined on query.
  - **Table Per Class**: Separate tables, no joins.

**4. Collections**:
- **Java**: `List`, `Set`, `Map`.
- **Database**: Only tables (unordered sets of rows).
- **JPA Solution**: `@OneToMany`, `@ManyToMany` annotations with fetch strategies.

#### Hibernate Session & Persistence Context

**Persistence Context**: In-memory cache of entities.

**Lifecycle States**:
1. **Transient**: New object, not yet persisted.
   ```java
   User user = new User();  // Transient
   ```
2. **Managed**: Tracked by persistence context.
   ```java
   entityManager.persist(user);  // Now managed
   user.setEmail("new@example.com");  // Change tracked
   // At transaction commit, Hibernate issues UPDATE
   ```
3. **Detached**: Was managed, but transaction ended.
   ```java
   entityManager.close();  // user now detached
   user.setEmail("another@example.com");  // Change NOT tracked
   ```
4. **Removed**: Marked for deletion.
   ```java
   entityManager.remove(user);  // Will be deleted at commit
   ```

**Dirty Checking**: Hibernate tracks changes to managed entities. At commit:
1. Compares current state to snapshot (loaded from DB).
2. Generates UPDATE for changed fields.
3. No need for explicit `save()` call.

**Example**:
```java
@Transactional
public void updateScore(UUID userId, BigDecimal delta) {
    User user = userRepository.findById(userId).orElseThrow();
    // user is MANAGED
    
    user.setAccountabilityScore(user.getAccountabilityScore().add(delta));
    // No userRepository.save() needed!
    
    // At method end, @Transactional commits:
    // Hibernate sees user.score changed, issues: UPDATE users SET accountability_score = ? WHERE id = ?
}
```

#### N+1 Query Problem

**The Trap**:
```java
List<Goal> goals = goalRepository.findAll();  // 1 query: SELECT * FROM goals
for (Goal goal : goals) {
    System.out.println(goal.getUser().getName());  // N queries: SELECT * FROM users WHERE id = ?
}
// Total: 1 + N queries (if 100 goals, 101 queries!)
```

**Solutions**:
1. **JOIN FETCH**:
   ```java
   @Query("SELECT g FROM Goal g JOIN FETCH g.user")
   List<Goal> findAllWithUsers();
   // 1 query: SELECT * FROM goals g JOIN users u ON g.user_id = u.id
   ```
2. **Entity Graph**:
   ```java
   @EntityGraph(attributePaths = {"user"})
   List<Goal> findAll();
   ```
3. **Batch Fetch**:
   ```java
   @BatchSize(size = 10)
   @OneToMany
   private List<Goal> goals;
   // Hibernate batches: SELECT * FROM goals WHERE user_id IN (?, ?, ?, ...)
   ```

---

### A.9 Database Indexing Theory

#### Theory: Why Queries Are Slow

**Full Table Scan**: To find `user` with `email = "alice@example.com"`, database reads **every row** in `users` table.
- **Time Complexity**: O(n) (n = number of rows).
- **Disk I/O**: If table has 1M rows, read 1M rows from disk.

**Solution**: **Index** (like book index).

#### Index Structures

**1. B-Tree Index** (Most Common):
- **Structure**: Balanced tree. Each node contains sorted keys and pointers.
- **Properties**:
  - All leaf nodes at same depth (balanced).
  - Keys in sorted order.
- **Search**: O(log n). If 1M rows, max 20 comparisons (2^20 ≈ 1M).
- **Insert/Delete**: O(log n). Tree rebalances if needed.

**Example**:
```
CREATE INDEX idx_users_email ON users(email);
```
- Postgres builds B-tree with `email` values.
- Query `SELECT * FROM users WHERE email = ?` uses index: O(log n) instead of O(n).

**2. Hash Index**:
- **Structure**: Hash table (key → bucket).
- **Search**: O(1) average.
- **Limitation**: Only equality checks (`=`). Cannot do range queries (`<`, `>`, `LIKE`).

**3. GiST/GIN (Generalized Index)**:
- **Use Case**: Full-text search, JSON queries, geometric data.
- **Example**:
  ```sql
  CREATE INDEX idx_goals_criteria ON goals USING GIN (criteria_config);
  SELECT * FROM goals WHERE criteria_config @> '{"metric": "pages"}';
  ```

#### Index Tradeoffs

**Pros**:
- **Faster Queries**: O(log n) vs O(n).

**Cons**:
- **Slower Writes**: Every INSERT/UPDATE/DELETE must update index (additional disk I/O).
- **Storage Overhead**: Index consumes disk space (often 10-30% of table size).
- **Maintenance**: Indexes can become fragmented; need periodic `REINDEX`.

**Best Practices**:
- Index columns used in `WHERE`, `JOIN`, `ORDER BY`.
- Don't over-index (diminishing returns; slows writes).
- Composite indexes: `CREATE INDEX ON users(last_name, first_name)` (useful for `WHERE last_name = ? AND first_name = ?`).

#### Our Indexes

```sql
CREATE INDEX idx_users_timezone ON users(timezone);
-- Why: NagScheduler queries: SELECT * FROM users WHERE timezone = ?

CREATE INDEX idx_goals_user_status ON goals(user_id, status);
-- Why: Dashboard queries: SELECT * FROM goals WHERE user_id = ? AND status = 'ACTIVE'

CREATE INDEX idx_audit_logs_date ON audit_logs(audit_date);
-- Why: Historical queries: SELECT * FROM audit_logs WHERE audit_date BETWEEN ? AND ?

CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read);
-- Why: Polling queries: SELECT * FROM notifications WHERE user_id = ? AND is_read = false
```

---

### A.10 Concurrency Control & Race Conditions

#### Theory: Race Conditions

**Scenario**: Two users click "Submit Audit" at same time for same goal.

**Naive Implementation**:
```java
// Thread A and Thread B execute concurrently:
AuditLog existing = auditRepository.findByGoalIdAndDate(goalId, today);  // Both see null
if (existing == null) {  // Both pass check
    auditRepository.save(new AuditLog(...));  // Both insert!
}
// Result: Two audit logs for same goal on same day (violates business rule)
```

**Why Check-Then-Act is Broken**:
- **Time-of-Check to Time-of-Use (TOCTOU)**: State can change between check and action.
- **Non-Atomic**: Two separate operations (read, write).

#### Solutions

**1. Database Unique Constraint** (Our Choice):
```sql
ALTER TABLE audit_logs ADD CONSTRAINT uq_goal_date UNIQUE (goal_id, audit_date);
```
- **How**: Database enforces at commit time.
- **Thread A**: Commits first → success.
- **Thread B**: Commits second → `ConstraintViolationException`.
- **Handling**:
  ```java
  try {
      auditRepository.save(audit);
  } catch (DataIntegrityViolationException e) {
      throw new AuditAlreadySubmittedException("Already submitted today");
  }
  ```

**Why This Works**:
- **Atomic**: Constraint check + insert in single database operation.
- **Database-Level**: Java cannot screw this up (even if multiple servers).

**2. Pessimistic Locking**:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Goal findById(UUID id);
```
- **How**: Database row lock. First thread locks row; second thread blocks until first commits.
- **Pros**: Prevents conflict.
- **Cons**: Reduces concurrency (threads wait); risk of deadlock.

**3. Optimistic Locking**:
```java
@Version
private Long version;  // Auto-incremented on each update
```
- **How**: At commit, check version hasn't changed. If changed, rollback and retry.
- **Pros**: High concurrency (no locks).
- **Cons**: Retry overhead; possible livelock.

**4. Application-Level Locks** (Distributed Locks):
```java
try {
    redissonClient.getLock("audit:" + goalId).lock();
    // Critical section
} finally {
    lock.unlock();
}
```
- **Use Case**: Multi-step operation across services.
- **Cons**: Complexity; what if lock holder crashes?

---

### A.11 HTTP Semantics & Status Codes

#### Theory: HTTP as an Application Protocol

**HTTP** is not just "request-response". It's a **state transfer protocol** with rich semantics.

#### Status Code Categories

**1xx (Informational)**: Request received, processing.
- `100 Continue`: Client should send request body.
- `101 Switching Protocols`: WebSocket upgrade.

**2xx (Success)**:
- `200 OK`: Standard success. Response has body.
- `201 Created`: Resource created. `Location` header points to new resource.
- `204 No Content`: Success, but no body (e.g., DELETE).

**3xx (Redirection)**:
- `301 Moved Permanently`: Resource permanently moved. Update bookmarks.
- `302 Found`: Temporary redirect.
- `304 Not Modified`: Resource unchanged (ETag match). Use cached version.

**4xx (Client Error)**:
- `400 Bad Request`: Malformed request (invalid JSON, missing field).
- `401 Unauthorized`: **Authentication** required (misnomer; should be "Unauthenticated"). Send valid credentials.
- `403 Forbidden`: **Authorization** failed. User authenticated but lacks permission.
- `404 Not Found`: Resource doesn't exist.
- `409 Conflict`: Request conflicts with current state (e.g., duplicate username).
- `422 Unprocessable Entity`: Request valid but semantically incorrect (e.g., email format invalid).
- `423 Locked`: Resource locked (our use: user in lockout).
- `429 Too Many Requests`: Rate limit exceeded.

**5xx (Server Error)**:
- `500 Internal Server Error`: Unhandled exception.
- `502 Bad Gateway`: Upstream server (e.g., agent) returned invalid response.
- `503 Service Unavailable`: Server overloaded or down (retry later).
- `504 Gateway Timeout`: Upstream server didn't respond in time.

#### Our Status Code Strategy

**Audit Submission**:
- `200 OK`: Audit processed successfully.
- `400 Bad Request`: File type/size invalid.
- `401 Unauthorized`: No JWT or invalid JWT.
- `423 Locked`: User in lockout (cannot submit).
- `409 Conflict`: Audit already submitted today.
- `500 Internal Server Error`: Unexpected error (agent crash, database down).
- `504 Gateway Timeout`: Agent didn't respond in 30s.

**Why Specific Codes Matter**:
- **Client Behavior**: Frontend can handle each case differently.
  - `401` → Redirect to login.
  - `423` → Show lockout screen.
  - `500` → Show generic error.
  - `504` → Show "Agent busy, try again".
- **Monitoring**: Prometheus counts 5xx errors → alerts.

---

### A.12 RESTful API Design Principles

#### Theory: REST (Representational State Transfer)

**REST** is an architectural style, not a protocol. Key constraints:

**1. Client-Server**: Separation of concerns. Client handles UI, server handles data/logic.

**2. Stateless**: Each request self-contained (no server-side session). Scalability benefit.

**3. Cacheable**: Responses marked as cacheable/non-cacheable (HTTP cache headers).

**4. Uniform Interface**:
- **Resources**: Everything is a resource (e.g., Goal, User, Audit).
- **Resource Identifiers**: URIs (e.g., `/api/goals/123`).
- **Representations**: JSON, XML, etc.
- **Self-Descriptive Messages**: Response contains media type (`Content-Type: application/json`), status code, etc.

**5. Layered System**: Client doesn't know if talking to origin server or proxy (enables load balancers, CDNs).

#### REST vs RPC

**RPC (Remote Procedure Call)**:
```
POST /api/createGoal
POST /api/deleteGoal
POST /api/getGoal
```
- **Mindset**: Calling remote functions.
- **Con**: Ignores HTTP verbs; all POST.

**REST**:
```
POST   /api/goals      # Create
GET    /api/goals/123  # Read
PUT    /api/goals/123  # Update
DELETE /api/goals/123  # Delete
```
- **Mindset**: Manipulating resources.
- **Pros**: Semantic HTTP verbs; cache-friendly (GET idempotent).

#### Idempotency

**Definition**: Repeated calls have same effect as single call.

- **GET, PUT, DELETE**: Idempotent.
  - `DELETE /api/goals/123` twice → first deletes, second returns 404. End state identical.
- **POST**: **Not idempotent**.
  - `POST /api/goals` twice → creates two goals.
  - **Solution**: Use idempotency key (header `Idempotency-Key: UUID`). Server deduplicates.

---

### A.13 File Upload Security (Defense in Depth)

#### Threat Model

**1. Malicious File Type**:
- **Attack**: Upload `virus.exe` renamed to `virus.jpg`.
- **Exploit**: If server executes file, attacker gains code execution.

**2. XXE (XML External Entity)**:
- **Attack**: Upload SVG with embedded XML:
  ```xml
  <!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
  <svg>&xxe;</svg>
  ```
- **Exploit**: Read server files.

**3. DoS via Large Files**:
- **Attack**: Upload 10GB file.
- **Exploit**: Server OOM (Out of Memory) crash.

**4. Path Traversal**:
- **Attack**: Filename `../../etc/passwd`.
- **Exploit**: Overwrite system files.

**5. Content Sniffing**:
- **Attack**: Upload HTML disguised as image.
- **Exploit**: Browser renders as HTML (XSS).

#### Defenses

**1. Validate Content-Type** (Weak):
```java
if (!file.getContentType().startsWith("image/")) {
    throw new InvalidFileTypeException();
}
```
- **Why Weak**: Attacker controls this header (easily spoofed).

**2. Check Magic Bytes** (Strong):
```java
byte[] header = file.getBytes();
if (header[0] != (byte)0xFF || header[1] != (byte)0xD8) {  // JPEG magic bytes
    throw new InvalidFileTypeException();
}
```
- **Magic Bytes**: First few bytes identify file type.
  - JPEG: `FF D8 FF`
  - PNG: `89 50 4E 47`
  - GIF: `47 49 46 38`
- **Attacker**: Cannot fake (binary signatures).

**3. Size Limit**:
```java
if (file.getSize() > 5_000_000) {  // 5MB
    throw new FileTooLargeException();
}
```
- **Spring Boot Config**:
  ```yaml
  spring:
    servlet:
      multipart:
        max-file-size: 5MB
        max-request-size: 5MB
  ```
- **Defense**: Rejects before loading into memory.

**4. Isolate Storage**:
- **Don't**: Store in `/var/www/html/uploads` (web-accessible).
- **Do**: Store in GCS (requires signed URL for access).

**5. Content-Disposition Header**:
```java
response.setHeader("Content-Disposition", "attachment; filename=\"proof.jpg\"");
```
- **Why**: Forces browser to download (not render). Prevents XSS if file is HTML.

**6. Scan for Viruses** (Production):
- Integrate ClamAV or VirusTotal API.
- Scan before storing.

---

### A.14 Resilience Patterns (Fail-Safe, Timeouts, Retries)

#### Theory: Failures in Distributed Systems

**Murphy's Law**: Anything that can fail, will fail.

**Failure Modes**:
1. **Service Down**: Agent container crashed.
2. **Network Partition**: Agent reachable but database not.
3. **Slow Responses**: Agent overloaded (1 request = 60s).
4. **Cascading Failures**: One slow service causes upstream timeouts → entire system down.

#### Resilience Patterns

**1. Timeouts**:
```java
webClient.post()
    .uri(agentUrl)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(AgentResponse.class)
    .timeout(Duration.ofSeconds(30))  // Kill after 30s
    .block();
```
- **Why**: Without timeout, thread waits forever (thread pool exhausted → service down).
- **Tuning**: Set timeout > p99 latency but << user patience (30s reasonable for ML inference).

**2. Fail-Safe (Graceful Degradation)**:
```java
try {
    AgentResponse response = agentClient.judge(audit);
    return response.verdict;  // PASS or FAIL
} catch (Exception e) {
    log.error("Agent unavailable", e);
    return "TECHNICAL_DIFFICULTY";  // Don't penalize user
}
```
- **Philosophy**: Prefer false negatives over false positives. Better to miss a cheat than penalize innocent user for our bug.

**3. Retries with Exponential Backoff**:
```java
Retry retry = Retry.backoff(3, Duration.ofSeconds(1));  // 1s, 2s, 4s
webClient.post(...)
    .retryWhen(retry)
    .block();
```
- **Why**: Transient failures (network blip) may resolve in seconds.
- **Exponential Backoff**: Prevents thundering herd (1000 clients retry simultaneously → overload server again).

**4. Circuit Breaker** (Not Yet Implemented):
```java
@CircuitBreaker(name = "agent", fallbackMethod = "fallback")
public AgentResponse judge(AuditRequest req) {
    return agentClient.call(req);
}
```
- **States**:
  - **Closed**: Normal operation.
  - **Open**: After N failures, stop calling agent (fail fast).
  - **Half-Open**: After timeout, try one request. If succeeds, close. If fails, reopen.
- **Why**: If agent down, don't waste resources on doomed requests.

**5. Bulkheads** (Isolation):
- **Concept**: Separate thread pools for different operations.
- **Why**: If "Upload Proof" overloads thread pool, "Get Dashboard" still works (different pool).

---

### A.15 Scheduler Design (Cron vs Fixed Delay)

#### Theory: Periodic Task Execution

**Requirements**: Check all users every 15 minutes to see if they missed deadlines.

**Naive Approach**: One cron job per user.
- **Problem**: 10,000 users = 10,000 scheduled tasks (infeasible).

**Our Approach**: Single scheduler iterates all users.

#### Spring Scheduling Annotations

**1. `@Scheduled(fixedRate = 900000)`** (Fixed Rate):
- **Behavior**: Run every 15 minutes, measured from **start time**.
- **Example**: Task starts at 00:00, takes 2 minutes → Next start at 00:15 (even though previous ended at 00:02).
- **Risk**: If task takes > 15 minutes, overlapping executions (unless `@EnableAsync` + thread pool limits).

**2. `@Scheduled(fixedDelay = 900000)`** (Fixed Delay):
- **Behavior**: Wait 15 minutes **after completion** before next run.
- **Example**: Task starts at 00:00, ends at 00:02 → Next start at 00:17.
- **Pros**: No overlaps.
- **Cons**: Drift (if task consistently slow, schedule drifts).

**3. `@Scheduled(cron = "0 */15 * * * *")`** (Cron):
- **Behavior**: Run at specific times (e.g., 00:00, 00:15, 00:30, ...).
- **Syntax**: `second minute hour day month weekday`.
- **Example**: `0 0 3 * * *` = "3am every day".
- **Pros**: Predictable times.

**Our Choice**: `fixedRate` (every 15 minutes from start).

#### Time Zone Handling

**Challenge**: Users in different time zones.
- Alice (PST): Goal review at 9am PST.
- Bob (EST): Goal review at 9am EST.

**Naive Solution**: Store `reviewTime` as local time (`09:00`).
- **Problem**: Server doesn't know time zone → can't compute "is it past 9am in user's zone?".

**Our Solution**:
1. **Store**: `reviewTime` in UTC (`TIME` column, stored as UTC).
2. **User Timezone**: Store in `users.timezone` (e.g., `America/Los_Angeles`).
3. **Scheduler Logic**:
   ```java
   ZoneId userZone = ZoneId.of(user.getTimezone());
   ZonedDateTime nowInUserZone = ZonedDateTime.now(userZone);
   LocalTime userLocalTime = nowInUserZone.toLocalTime();
   
   if (userLocalTime.isAfter(goal.getReviewTime())) {
       // User missed deadline
   }
   ```

**Quiet Hours**: Don't nag between 11pm and 6am (local time).
- **Why**: User experience (don't wake users).

---

### A.16 Testing Pyramid & Strategies

#### Theory: Test Levels

**Test Pyramid** (bottom to top):
1. **Unit Tests** (70%): Test single class in isolation.
2. **Integration Tests** (20%): Test multiple components together.
3. **End-to-End Tests** (10%): Test full user flow (UI → API → DB).

**Why Pyramid Shape**:
- **Unit tests**: Fast (milliseconds), cheap to maintain, pinpoint failures.
- **E2E tests**: Slow (seconds), brittle (UI changes break tests), hard to debug.

#### Unit Testing Example

**What We Test**: `ScoreService.applyDelta()` logic.

**Tools**: JUnit 5 + Mockito.

**Test**:
```java
@Test
void applyDelta_whenScoreDropsBelowThreshold_locksGoals() {
    // Arrange
    User user = new User();
    user.setAccountabilityScore(BigDecimal.valueOf(3.10));
    
    GoalRepository mockRepo = mock(GoalRepository.class);
    ScoreService service = new ScoreService(mockRepo);
    
    // Act
    service.applyDelta(user, BigDecimal.valueOf(-0.50));  // 3.10 - 0.50 = 2.60 (below 3.0)
    
    // Assert
    assertEquals(BigDecimal.valueOf(2.60), user.getAccountabilityScore());
    verify(mockRepo).findActiveGoalsByUser(user.getId());  // Verify lockdown triggered
}
```

**What We Mocked**: `GoalRepository` (don't need real database).

**What We Verified**: Correct score calculation + lockdown triggered.

#### Integration Testing Example

**What We Test**: Full audit submission flow (Controller → Service → Repository → DB).

**Tools**: `@SpringBootTest` + `@Transactional` (rollback after test).

**Test**:
```java
@SpringBootTest
@Transactional
class AuditFlowTest {
    @Autowired AuditController controller;
    @Autowired UserRepository userRepo;
    
    @Test
    void submitAudit_createsAuditLogAndUpdatesScore() {
        // Arrange
        User user = userRepo.save(new User("alice@example.com"));
        Goal goal = goalRepo.save(new Goal(user, "Read 10 pages"));
        
        MockMultipartFile file = new MockMultipartFile("proof", "proof.jpg", "image/jpeg", new byte[100]);
        
        // Act
        AuditResponseDto response = controller.submitAudit(goal.getId(), file);
        
        // Assert
        assertEquals("PASS", response.getVerdict());
        
        User updatedUser = userRepo.findById(user.getId()).get();
        assertEquals(BigDecimal.valueOf(5.50), updatedUser.getAccountabilityScore());  // 5.00 + 0.50
    }
}
```

**Why Integration Test**:
- Tests real database (H2 in-memory for speed).
- Tests `@Transactional` behavior (rollback on exception).
- Tests Jackson serialization (DTO ↔ JSON).

#### Mocking External Services

**Problem**: Tests shouldn't call real Agent API (slow, flaky, costs money).

**Solution**: Mock `AgentClient`:
```java
@MockBean AgentClient agentClient;

@BeforeEach
void setup() {
    when(agentClient.judge(any())).thenReturn(
        new AgentResponse("PASS", "Looks good!", 0.50)
    );
}
```

**Alternative**: Use `WireMock` (HTTP mock server):
```java
stubFor(post("/internal/judge/audit")
    .willReturn(okJson("{\"verdict\": \"PASS\"}")));
```

---

### A.17 Cryptographic Hashing (SHA-256)

#### Theory: Hash Functions

**Definition**: Function `H(input) = output` where:
1. **Deterministic**: Same input → same output.
2. **Fast**: Compute in milliseconds.
3. **One-Way**: Given output, infeasible to find input (preimage resistance).
4. **Collision-Resistant**: Infeasible to find two inputs with same output.

**Use Cases**:
1. **Password Hashing**: Store `H(password)`, not plaintext.
2. **Data Integrity**: Checksum (e.g., file download verification).
3. **Content-Addressable Storage**: Filename = hash (deduplication).

#### SHA-256 (Secure Hash Algorithm 256-bit)

**Algorithm**: Merkle-Damgård construction with compression function.

**Properties**:
- **Output Size**: 256 bits (32 bytes, 64 hex characters).
- **Security**: No known practical collision attacks (as of 2024).
- **Speed**: ~500 MB/s (software), ~5 GB/s (hardware).

**Example**:
```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hash = digest.digest("hello".getBytes());
// hash = e3b0c44298fc1c14...
```

#### Our Use: Content-Based Filenames

**Goal**: Store proof image in GCS with unique filename.

**Naive**: Use `UUID.randomUUID()` → `proof-123e4567-e89b-12d3-a456-426614174000.jpg`.
- **Problem**: Same image uploaded twice → two copies (wasted storage).

**Better**: Use hash of content → `proof-e3b0c44298fc1c14.jpg`.
- **Benefit**: Same image → same hash → overwrites old copy (deduplication).

**Implementation**:
```java
byte[] fileBytes = file.getBytes();
String hash = DigestUtils.sha256Hex(fileBytes);
String filename = goalId + "/" + date + "_" + hash + ".jpg";
```

**Why Not MD5**: MD5 is broken (collision attacks exist). Use SHA-256 or SHA-3.

---

## Appendix B: GCP-Specific Concepts

### B.1 Cloud SQL vs Self-Managed Postgres

**Cloud SQL (Managed)**:
- **Pros**: Automated backups, point-in-time recovery, auto-scaling storage, security patches, high availability (failover).
- **Cons**: Cost (2-3x vs raw compute), limited control (cannot tweak kernel params).

**Self-Managed (Compute Engine + Postgres)**:
- **Pros**: Full control, cheaper.
- **Cons**: You handle backups, HA, patching, tuning.

**Our Choice**: Cloud SQL (operational simplicity).

### B.2 Cloud Storage vs Persistent Disk

**Cloud Storage (GCS)**:
- **Object Storage**: Key-value store (key = filename, value = bytes).
- **Use Case**: Large blobs (images, videos, backups).
- **Pros**: Unlimited capacity, HTTP API, lifecycle policies (auto-delete after 60 days).
- **Cons**: Higher latency (~10ms vs ~1ms for disk), not a filesystem (no directories, only prefixes).

**Persistent Disk**:
- **Block Storage**: Acts like hard drive (ext4 filesystem).
- **Use Case**: Database files, application binaries.

**Our Choice**: GCS for proofs (don't need filesystem semantics, want lifecycle policies).

### B.3 Cloud Run vs GKE vs Compute Engine

**Cloud Run** (Serverless Containers):
- **Model**: Deploy container, auto-scales to zero.
- **Pros**: No cluster management, pay per request, auto-scaling.
- **Cons**: Startup latency (cold start ~1s), stateless only.

**GKE (Google Kubernetes Engine)**:
- **Model**: Managed Kubernetes cluster.
- **Pros**: Full Kubernetes features (stateful sets, custom schedulers).
- **Cons**: Cluster overhead (master nodes, etcd), cost (always-on VMs).

**Compute Engine** (VMs):
- **Model**: Raw VMs.
- **Pros**: Maximum control.
- **Cons**: You manage OS, scaling, load balancing.

**Our Choice**: Cloud Run (simplicity, auto-scale).

---

## Appendix C: Performance & Optimization

### C.1 Connection Pooling

**Problem**: Opening database connection is expensive (~50ms).
- **Naive**: Open connection per request → 50ms overhead.

**Solution**: Connection pool (e.g., HikariCP).
- **How**: Pre-open 10 connections, reuse across requests.
- **Benefit**: ~0ms overhead (connection already open).

**Spring Boot Config**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### C.2 N+1 Query Prevention (Revisited)

**Tool**: Hibernate Statistics.
```java
@Bean
public StatisticsFactory statisticsFactory() {
    return new StatisticsFactory();
}
```

**In Tests**:
```java
Statistics stats = sessionFactory.getStatistics();
stats.clear();

// Execute test

assertThat(stats.getPrepareStatementCount()).isLessThan(10);  // Fail if > 10 queries
```

---

**End of Deep Theory Appendix**. You are now equipped with CS fundamentals and real-world engineering trade-offs to ace any backend interview.
