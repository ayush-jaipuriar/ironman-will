# Iron Will Core: The Comprehensive Developer's Handbook & Study Guide

**Target Audience**: Mid-level Engineers learning Spring Boot & Enterprise Architecture.
**Goal**: To provide an end-to-end, line-by-line understanding of the Iron Will Core backend.

---

## Table of Contents
1.  [Chapter 1: Architectural Philosophy & The "Why"](#chapter-1-architectural-philosophy--the-why)
2.  [Chapter 2: Project Autopsy (Structure & Dependencies)](#chapter-2-project-autopsy-structure--dependencies)
3.  [Chapter 3: Spring Boot Mechanics (Configuration & DI)](#chapter-3-spring-boot-mechanics-configuration--di)
4.  [Chapter 4: The Database Layer (JPA & Entities)](#chapter-4-the-database-layer-jpa--entities)
5.  [Chapter 5: The Security Layer (OAuth2 & JWT)](#chapter-5-the-security-layer-oauth2--jwt)
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

## Chapter 5: The Security Layer (OAuth2 & JWT)

This is the most complex part of the system. Let's trace the flow.

### 5.1 Step 1: Login (`CustomOAuth2UserService`)
This class extends Spring's default behavior.
**The Logic**:
1.  User logs in with Google.
2.  Google returns a profile (email, name).
3.  We check our DB: `userRepository.findByEmail(email)`.
4.  **If New**: Create a `User`, set Score to 5.0, Role to USER. Save.
5.  **If Existing**: Return the user.
**The Why**: This is "Just-In-Time" (JIT) provisioning. We don't have a separate "Sign Up" page. Access implies registration.

### 5.2 Step 2: Token Minting (`OAuth2LoginSuccessHandler`)
Once the user is authenticated via Google:
1.  We grab their email.
2.  We use `JwtService` to sign a token using `HMAC-SHA256`.
3.  We explicitly embed their Roles (`ROLE_USER`) into the token claims.
4.  We return the token as JSON.

### 5.3 Step 3: Authorization (`JwtAuthenticationFilter`)
This filter runs on *every* request.
1.  **Extract**: Checks `Authorization` header for `Bearer <token>`.
2.  **Verify**: Uses `JwtService` to check the signature (ensuring no one tampered with it).
3.  **Authenticate**: Loads the user details and puts them in the `SecurityContext`.
**The Why**: This decouples authentication (Login) from authorization (Access). The API doesn't care *how* you got the token, only that it is valid.

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

**End of Guide**. You now possess the knowledge of the Architect. Go forth and code.
