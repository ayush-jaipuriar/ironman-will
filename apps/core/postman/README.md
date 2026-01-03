# Iron Will Core API - Postman Collection

This folder contains the Postman collection for testing the Iron Will Core backend API.

## Quick Start

### 1. Import Collection

1. Open Postman
2. Click **Import** button (top left)
3. Select `Iron_Will_Core_API.postman_collection.json`
4. Click **Import**

### 2. Configure Variables

The collection uses these variables (automatically managed):

- `base_url`: API base URL (default: `http://localhost:8080`)
- `jwt_token`: JWT authentication token (auto-saved after login)
- `goal_id`: Created goal ID (auto-saved after goal creation)
- `notification_id`: Notification ID (auto-saved after fetching notifications)

**To change base URL**:
1. Click on collection name
2. Go to **Variables** tab
3. Update `base_url` current value

### 3. Start Testing

**Recommended Flow**:

1. **Health Check** → Verify server is running
2. **Login with Email/Password** → Get JWT token (auto-saved)
3. **Get Current User Profile** → Verify authentication works
4. **Create Goal** → Goal ID auto-saved
5. **Get All Goals** → See your goals
6. **Submit Audit Proof** → Upload image file
7. **Get Unread Notifications** → Check notifications
8. **Mark Notification as Read** → Clear notification

## Collection Structure

```
Iron Will Core API/
├── Authentication/
│   ├── Login with Email/Password
│   ├── Get Current User Profile
│   └── Google OAuth Callback (Manual)
├── User Management/
│   └── Update User Timezone
├── Goals/
│   ├── Create Goal
│   ├── Get All Goals
│   ├── Get Goal by ID
│   └── Update Goal
├── Audits/
│   ├── Submit Audit Proof (PASS)
│   ├── Submit Audit Proof (Invalid File Type)
│   └── Submit Audit While Locked
├── Notifications/
│   ├── Get Unread Notifications
│   ├── Mark Notification as Read
│   └── Mark All Notifications as Read
└── Health Check/
    └── Health Check
```

## Request Details

### Authentication

#### Login with Email/Password
```
POST {{base_url}}/auth/login
Content-Type: application/json

{
  "email": "your-email@example.com",
  "password": "your-password"
}

Response:
{
  "token": "eyJhbGc..."
}
```

**Test Script**: Automatically saves JWT token to `{{jwt_token}}` variable.

#### Get Current User Profile
```
GET {{base_url}}/auth/me
Authorization: Bearer {{jwt_token}}

Response:
{
  "email": "your-email@example.com",
  "fullName": "Your Name",
  "timezone": "America/New_York",
  "accountabilityScore": 5.00,
  "lockedUntil": null
}
```

### Goals

#### Create Goal
```
POST {{base_url}}/api/goals
Authorization: Bearer {{jwt_token}}
Content-Type: application/json

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

**Test Script**: Automatically saves goal ID to `{{goal_id}}` variable.

#### Get All Goals
```
GET {{base_url}}/api/goals
Authorization: Bearer {{jwt_token}}

Response: Array of goals
```

### Audits

#### Submit Audit Proof
```
POST {{base_url}}/api/goals/{{goal_id}}/audit
Authorization: Bearer {{jwt_token}}
Content-Type: multipart/form-data

Form Data:
- proof: <select JPG or PNG file>

Response:
{
  "verdict": "PASS",
  "remarks": "Great work! Goal achieved.",
  "scoreDelta": 0.50,
  "newScore": 5.50
}
```

**Important**: 
- Use **form-data** body type
- Select an image file (JPG/PNG only)
- Max file size: 5MB

### Notifications

#### Get Unread Notifications
```
GET {{base_url}}/api/notifications/unread
Authorization: Bearer {{jwt_token}}

Response: Array of notifications
```

**Test Script**: Automatically saves first notification ID to `{{notification_id}}` variable.

## Testing Scenarios

### Scenario 1: Happy Path
1. Health Check → Login → Create Goal → Submit Audit → Check Score

### Scenario 2: Error Handling
1. Login with wrong password → Expect 401
2. Submit invalid file type → Expect 400
3. Submit audit without token → Expect 403

### Scenario 3: Lockout Flow
1. Manually set user score to 3.10 (database)
2. Submit failing audit
3. Try to submit another audit → Expect 423 Locked

## Tips

### Auto-Save Variables
All requests with test scripts automatically save important values:
- Login → Saves JWT token
- Create Goal → Saves goal ID
- Get Notifications → Saves first notification ID

### Manual Token Update
If token expires or you need to manually set it:
1. Click collection name
2. Go to **Variables** tab
3. Paste token into `jwt_token` current value
4. Click **Save**

### File Upload
For audit submission:
1. Select request
2. Go to **Body** tab
3. Ensure **form-data** is selected
4. Click **Select Files** next to `proof` field
5. Choose JPG or PNG image

### Running All Tests
1. Right-click on collection name
2. Select **Run collection**
3. Select all requests (or specific folder)
4. Click **Run Iron Will Core API**
5. View results in Collection Runner

## Common Issues

### Issue: Token Not Auto-Saved
**Solution**: Check **Tests** tab in Login request contains:
```javascript
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.collectionVariables.set('jwt_token', jsonData.token);
}
```

### Issue: 401 Unauthorized on Protected Endpoints
**Solutions**:
1. Ensure you logged in first
2. Check token is saved in collection variables
3. Verify Authorization header is set to `Bearer {{jwt_token}}`
4. Token might be expired → Login again

### Issue: File Upload Fails
**Solutions**:
1. Ensure file is JPG or PNG
2. Check file size < 5MB
3. Use **form-data** (not binary or raw)
4. Field name must be exactly `proof`

## Environment Setup

### Local Development
```
base_url: http://localhost:8080
```

### Cloud Run (Staging)
```
base_url: https://core-staging-xxx.run.app
```

### Cloud Run (Production)
```
base_url: https://core-production-xxx.run.app
```

To switch environments:
1. Create Environment in Postman
2. Set `base_url` variable
3. Select environment from dropdown (top right)

## Additional Resources

- **Full Testing Guide**: `../TESTING_GUIDE.md`
- **API Documentation**: Start server and visit `http://localhost:8080/swagger-ui.html`
- **Study Guide**: `../STUDY_GUIDE.md`

---

**Need Help?** Refer to the comprehensive `TESTING_GUIDE.md` for detailed scenarios and troubleshooting.

