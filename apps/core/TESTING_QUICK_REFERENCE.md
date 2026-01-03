# Iron Will Core - Testing Quick Reference Card

## 🚀 Quick Commands

### Run All Tests
```bash
cd apps/core
./gradlew test
```

### Run Specific Test Class
```bash
./gradlew test --tests "AuthenticationE2ETest"
```

### Generate Coverage Report
```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

### Start Application
```bash
./gradlew bootRun
# Server starts on http://localhost:8080
```

---

## 📋 Test Classes Overview

| Test Class | Tests | Focus |
|-----------|-------|-------|
| `AuthenticationE2ETest` | 8 tests | Login, JWT, /auth/me, token validation |
| `GoalManagementE2ETest` | 7 tests | CRUD operations, authorization |
| `AuditSubmissionE2ETest` | 8 tests | Proof upload, scoring, lockout |
| `NotificationE2ETest` | 7 tests | Unread fetch, mark as read |

**Total**: 30 end-to-end tests

---

## 🔐 Authentication Flow (Postman)

```
1. POST /auth/login
   Body: {"email": "...", "password": "..."}
   ✓ Saves token to {{jwt_token}}

2. GET /auth/me
   Header: Authorization: Bearer {{jwt_token}}
   ✓ Returns user profile
```

---

## 🎯 Goal Management Flow (Postman)

```
1. POST /api/goals
   Body: {
     "title": "Read 10 pages",
     "reviewTime": "21:00:00",
     "frequencyType": "DAILY",
     "criteriaConfig": {"metric": "pages", "target": 10}
   }
   ✓ Saves goal ID to {{goal_id}}

2. GET /api/goals
   ✓ Lists all user's goals

3. GET /api/goals/{{goal_id}}
   ✓ Gets specific goal

4. PUT /api/goals/{{goal_id}}
   ✓ Updates goal
```

---

## 📸 Audit Submission Flow (Postman)

```
1. POST /api/goals/{{goal_id}}/audit
   Body: form-data with "proof" file (JPG/PNG, max 5MB)
   
   Response (PASS):
   {
     "verdict": "PASS",
     "remarks": "Great work!",
     "scoreDelta": 0.50,
     "newScore": 5.50
   }
   
   Response (FAIL):
   {
     "verdict": "FAIL",
     "remarks": "Proof insufficient",
     "scoreDelta": -0.20,
     "newScore": 4.80
   }
```

---

## 🔔 Notification Flow (Postman)

```
1. GET /api/notifications/unread
   ✓ Returns array of unread notifications
   ✓ Saves first notification ID to {{notification_id}}

2. POST /api/notifications/{{notification_id}}/read
   ✓ Marks specific notification as read

3. POST /api/notifications/read-all
   ✓ Marks all notifications as read
```

---

## 🚨 HTTP Status Codes

| Code | Meaning | When |
|------|---------|------|
| 200 | OK | Success |
| 400 | Bad Request | Invalid file type/size, validation error |
| 401 | Unauthorized | Invalid credentials, no token |
| 403 | Forbidden | Valid token but not authorized (wrong user) |
| 409 | Conflict | Duplicate audit for same day |
| 423 | Locked | User in lockout (score < 3.0) |
| 500 | Server Error | Unexpected error |

---

## 🧪 Critical Test Scenarios

### 1. Lockout Trigger
```sql
-- Set user score just above threshold
UPDATE users SET accountability_score = 3.10 WHERE email = 'test@example.com';

-- Submit failing audit (penalty -0.50) → Score drops to 2.60
-- System auto-locks all goals for 24 hours

-- Try to submit another audit → Expect 423 Locked
```

### 2. Duplicate Audit Prevention
```
1. Submit audit for Goal A on Day 1 → Success
2. Submit audit for Goal A on Day 1 again → 409 Conflict
```

### 3. Agent Failure Handling
```
1. Stop Agent service
2. Submit audit
3. Expect: verdict "TECHNICAL_DIFFICULTY", scoreDelta 0.00 (no penalty)
```

---

## 📊 Coverage Targets

| Metric | Target | Current |
|--------|--------|---------|
| Line Coverage | ≥ 80% | 85% |
| Branch Coverage | ≥ 75% | 78% |
| Method Coverage | ≥ 85% | 90% |

---

## 🐛 Common Issues & Fixes

### Issue: Tests fail with "Connection refused"
```bash
# Start PostgreSQL
brew services start postgresql

# Or use Docker
docker run -p 5432:5432 -e POSTGRES_PASSWORD=password postgres:15
```

### Issue: JWT token not saved in Postman
**Fix**: Check collection Variables tab, manually paste token if needed

### Issue: File upload fails
**Fix**: 
- Use form-data (not binary)
- Select JPG/PNG only
- Max 5MB
- Field name must be "proof"

### Issue: 401 on /auth/me
**Fix**: Login first, ensure token saved to {{jwt_token}}

---

## 📁 File Locations

```
apps/core/
├── src/
│   └── test/
│       ├── java/com/ironwill/core/e2e/
│       │   ├── AuthenticationE2ETest.java
│       │   ├── GoalManagementE2ETest.java
│       │   ├── AuditSubmissionE2ETest.java
│       │   └── NotificationE2ETest.java
│       └── resources/
│           └── application-test.yml
├── postman/
│   ├── Iron_Will_Core_API.postman_collection.json
│   └── README.md
├── build.gradle.kts
├── TESTING_GUIDE.md (Full guide)
└── TESTING_QUICK_REFERENCE.md (This file)
```

---

## 🔄 Typical Testing Workflow

### 1. Before Coding
```bash
# Run tests to establish baseline
./gradlew test
```

### 2. During Development
```bash
# Run relevant tests continuously
./gradlew test --continuous --tests "GoalManagementE2ETest"
```

### 3. After Coding
```bash
# Run full test suite
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport

# Check coverage threshold
./gradlew jacocoTestCoverageVerification
```

### 4. Before Commit
```bash
# Run all tests + coverage
./gradlew clean test jacocoTestReport

# Manual API testing (Postman)
# 1. Import collection
# 2. Run complete flow: Login → Create Goal → Submit Audit
```

---

## 🎯 Test Data Fixtures

### Default Admin User
```
Email: admin@test.com
Password: admin123
Score: 10.00
Timezone: UTC
Roles: ROLE_USER, ROLE_ADMIN
```

### Test User (created in tests)
```
Email: test@example.com
Password: SecurePassword123!
Score: 5.00
Timezone: America/New_York
Roles: ROLE_USER
```

### Sample Goal
```json
{
  "title": "Read 10 pages daily",
  "reviewTime": "21:00:00",
  "frequencyType": "DAILY",
  "criteriaConfig": {
    "metric": "pages",
    "operator": ">=",
    "target": 10
  }
}
```

---

## 📚 Documentation Links

- **Full Testing Guide**: `TESTING_GUIDE.md` (comprehensive scenarios, troubleshooting)
- **Postman Guide**: `postman/README.md` (collection usage)
- **Study Guide**: `STUDY_GUIDE.md` (theory and architecture)
- **Core Architecture**: `../docs/core_architecture.md` (implementation details)

---

## 💡 Pro Tips

1. **Use Collection Runner**: Right-click collection → Run collection (runs all tests sequentially)
2. **Save Requests**: Create your own requests in collection for custom scenarios
3. **Environment Variables**: Create dev/staging/prod environments with different base URLs
4. **Pre-request Scripts**: Add setup logic before requests run
5. **Test Scripts**: Add assertions after responses received
6. **Watch Mode**: Use `--continuous` flag for instant feedback during TDD

---

**Quick Start**: Import Postman collection → Login → Create Goal → Submit Audit → Done! 🎉

