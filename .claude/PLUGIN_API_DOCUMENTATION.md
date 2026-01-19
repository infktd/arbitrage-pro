# Arbitrage Pro Plugin API Documentation

## Overview
This document contains all API endpoints, request/response formats, and authentication requirements for the RuneLite plugin to interact with the Arbitrage Pro backend.

**Base URL**: `https://api.arbitragepro.com` (or your deployed domain)  
**Development Base URL**: `http://localhost:8000`

---

## Authentication

All authenticated endpoints require a JWT token in the `Authorization` header.

### Header Format
```
Authorization: Bearer <jwt_token>
```

The JWT token is obtained from the `/auth/login` or `/auth/register` endpoints and should be stored securely by the plugin. Token expires after 7 days.

---

## API Endpoints

### 1. User Registration

**Endpoint**: `POST /auth/register`  
**Authentication**: None  
**Description**: Create a new user account

#### Request Body
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

#### Response - Success (201)
```json
{
  "user_id": 123,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### Response - Error (409 - Email exists)
```json
{
  "error": "Email already exists"
}
```

#### Response - Error (400 - Missing fields)
```json
{
  "error": "Email and password are required"
}
```

---

### 2. User Login

**Endpoint**: `POST /auth/login`  
**Authentication**: None  
**Description**: Authenticate user and receive JWT token

#### Request Body
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

#### Response - Success (200)
```json
{
  "user_id": 123,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "licenses": [
    {
      "license_id": 456,
      "user_id": 123,
      "runescape_username": "PlayerName",
      "status": "active",
      "subscription_end": "2026-12-31T23:59:59Z"
    }
  ]
}
```

#### Response - Error (401 - Invalid credentials)
```json
{
  "error": "Invalid email or password"
}
```

---

### 3. Verify Token

**Endpoint**: `GET /auth/verify`  
**Authentication**: Required  
**Description**: Verify JWT token validity and check license status

#### Request Headers
```
Authorization: Bearer <jwt_token>
```

#### Response - Success (200)
```json
{
  "valid": true,
  "user_id": 123,
  "license_status": "active"
}
```

or if no active license:

```json
{
  "valid": true,
  "user_id": 123,
  "license_status": "no_active_license"
}
```

#### Response - Error (401 - Invalid/expired token)
```json
{
  "error": "Invalid or expired token"
}
```

---

### 4. Get Recommendations

**Endpoint**: `GET /recommendations`  
**Authentication**: Required  
**Description**: Get personalized arbitrage recommendations based on available GP and GE slots

#### Request Parameters (Query String)
```
runescape_username: string (required)
available_gp: integer (required) - Total GP available for trading
available_ge_slots: integer (required) - Number of available GE slots (1-8)
```

#### Example Request
```
GET /recommendations?runescape_username=PlayerName&available_gp=10000000&available_ge_slots=3
Authorization: Bearer <jwt_token>
```

#### Response - Success (200) - New Recommendation
```json
{
  "recommendation_id": 789,
  "item_id": 4151,
  "item_name": "Abyssal whip",
  "buy_price": 2500000,
  "buy_quantity": 1,
  "sell_price": 2580000,
  "expected_profit": 80000,
  "expected_roi_percent": 3.2
}
```

#### Response - Wait (200) - Active Trade In Progress
```json
{
  "action": "wait",
  "active_trade": {
    "trade_id": 999,
    "item_id": 4151,
    "item_name": "Abyssal whip",
    "buy_price": 2500000,
    "buy_quantity": 1,
    "buy_quantity_filled": 0,
    "sell_price": 2580000,
    "sell_quantity_filled": 0,
    "status": "buying",
    "created_at": "2026-01-18T10:30:00Z",
    "last_updated_at": "2026-01-18T10:30:00Z",
    "is_stale": false
  }
}
```

#### Response - Error (403 - No active license)
```json
{
  "error": "No active license found for this RuneScape username"
}
```

#### Response - Error (503 - No recommendations available)
```json
{
  "error": "No recommendations available. Hydra may not have run yet."
}
```

#### Response - Error (503 - All items being traded)
```json
{
  "error": "All recommended items are currently being traded. Please try again later."
}
```

#### Response - Error (400 - Insufficient GP)
```json
{
  "error": "Insufficient GP to trade any available items"
}
```

---

### 5. Create Trade

**Endpoint**: `POST /trades/create`  
**Authentication**: Required  
**Description**: Create a new active trade when user places a buy order

#### Request Body
```json
{
  "item_id": 4151,
  "buy_price": 2500000,
  "buy_quantity": 1,
  "sell_price": 2580000,
  "recommendation_id": 789
}
```

**Fields**:
- `item_id` (required): Integer - Item ID from recommendation
- `buy_price` (required): Integer - Price user is buying at
- `buy_quantity` (required): Integer - Quantity user is buying
- `sell_price` (optional): Integer - Expected sell price from recommendation
- `recommendation_id` (optional): Integer - Links to recommendation that generated this trade

#### Response - Success (201)
```json
{
  "trade_id": 999,
  "status": "buying"
}
```

#### Response - Error (403 - No active license)
```json
{
  "error": "No active license found"
}
```

---

### 6. Update Trade

**Endpoint**: `POST /trades/{trade_id}/update`  
**Authentication**: Required  
**Description**: Update trade status and quantities as the trade progresses

#### Path Parameters
- `trade_id`: Integer - The trade ID to update

#### Request Body
```json
{
  "status": "bought",
  "buy_quantity_filled": 1,
  "sell_quantity_filled": 0
}
```

**Fields** (all optional):
- `status`: String - One of: `"buying"`, `"bought"`, `"selling"`, `"completed"`
- `buy_quantity_filled`: Integer - How many items have been bought
- `sell_quantity_filled`: Integer - How many items have been sold

**Status Progression**:
1. `"buying"` - Initial state when buy order is placed
2. `"bought"` - Buy order completed
3. `"selling"` - Sell order placed
4. Use `/trades/{trade_id}/complete` when fully sold (not this endpoint)

#### Response - Success (200)
```json
{
  "success": true,
  "trade": {
    "trade_id": 999,
    "user_id": 123,
    "license_id": 456,
    "item_id": 4151,
    "buy_price": 2500000,
    "buy_quantity": 1,
    "buy_quantity_filled": 1,
    "sell_price": 2580000,
    "sell_quantity_filled": 0,
    "status": "bought",
    "created_at": "2026-01-18T10:30:00Z",
    "buy_completed_at": "2026-01-18T10:35:00Z",
    "sell_started_at": null,
    "last_updated_at": "2026-01-18T10:35:00Z"
  },
  "action": "sell",
  "sell_price": 2580000
}
```

**Action Field**:
- `"wait"` - Continue waiting (no action needed)
- `"sell"` - User should now place sell order at `sell_price`
- `"complete"` - Trade is complete

#### Response - Error (404 - Trade not found)
```json
{
  "error": "Trade not found or does not belong to user"
}
```

---

### 7. Get Active Trades

**Endpoint**: `GET /trades/active`  
**Authentication**: Required  
**Description**: Retrieve all active (non-completed) trades for the user

#### Request Headers
```
Authorization: Bearer <jwt_token>
```

#### Response - Success (200)
```json
{
  "trades": [
    {
      "trade_id": 999,
      "user_id": 123,
      "license_id": 456,
      "item_id": 4151,
      "item_name": "Abyssal whip",
      "buy_price": 2500000,
      "buy_quantity": 1,
      "buy_quantity_filled": 1,
      "sell_price": 2580000,
      "sell_quantity_filled": 0,
      "status": "bought",
      "created_at": "2026-01-18T10:30:00Z",
      "buy_completed_at": "2026-01-18T10:35:00Z",
      "sell_started_at": null,
      "last_updated_at": "2026-01-18T10:35:00Z",
      "is_stale": false
    }
  ]
}
```

**Note**: `is_stale` is `true` if `last_updated_at` is more than 15 minutes ago, indicating the plugin may not be updating properly.

---

### 8. Complete Trade

**Endpoint**: `POST /trades/{trade_id}/complete`  
**Authentication**: Required  
**Description**: Mark a trade as complete when sell order fills. Moves trade to completed_trades table.

#### Path Parameters
- `trade_id`: Integer - The trade ID to complete

#### Request Body
```json
{
  "sell_quantity_filled": 1,
  "actual_sell_price": 2590000
}
```

**Fields**:
- `sell_quantity_filled` (required): Integer - Quantity sold
- `actual_sell_price` (optional): Integer - Actual price sold at (if different from expected). If not provided, uses the `sell_price` from the trade record.

#### Response - Success (200)
```json
{
  "success": true,
  "profit": 90000,
  "roi_percent": 3.6
}
```

**Calculation**:
- `profit` = (actual_sell_price × sell_quantity_filled) - (buy_price × buy_quantity)
- `roi_percent` = (profit / (buy_price × buy_quantity)) × 100

#### Response - Error (404 - Trade not found)
```json
{
  "error": "Trade not found or does not belong to user"
}
```

---

## Plugin Flow Examples

### Example 1: Fresh Login - Get Recommendation

```
1. User logs in through plugin
   POST /auth/login
   → Receive token

2. Plugin requests recommendation
   GET /recommendations?runescape_username=PlayerName&available_gp=5000000&available_ge_slots=4
   → Receive: { "item_id": 4151, "buy_price": 2500000, "buy_quantity": 1, ... }

3. Plugin displays recommendation to user
```

### Example 2: User Places Buy Order

```
1. Plugin detects GE buy offer placed (via GrandExchangeOfferChanged event)
   - Matches item_id and buy_price from recommendation

2. Plugin creates trade
   POST /trades/create
   Body: { "item_id": 4151, "buy_price": 2500000, "buy_quantity": 1, ... }
   → Receive: { "trade_id": 999, "status": "buying" }

3. Plugin monitors GE for buy completion
```

### Example 3: Buy Order Completes

```
1. Plugin detects buy offer completed (GrandExchangeOfferChanged: state = BOUGHT)

2. Plugin updates trade
   POST /trades/999/update
   Body: { "status": "bought", "buy_quantity_filled": 1 }
   → Receive: { "action": "sell", "sell_price": 2580000 }

3. Plugin notifies user to place sell order at 2580000 gp
```

### Example 4: User Places Sell Order

```
1. Plugin detects sell offer placed (via GrandExchangeOfferChanged event)
   - Verifies price matches expected sell_price

2. Plugin updates trade
   POST /trades/999/update
   Body: { "status": "selling" }
   → Receive: { "action": "wait" }

3. Plugin monitors GE for sell completion
```

### Example 5: Sell Order Completes

```
1. Plugin detects sell offer completed (GrandExchangeOfferChanged: state = SOLD)

2. Plugin completes trade
   POST /trades/999/complete
   Body: { "sell_quantity_filled": 1, "actual_sell_price": 2590000 }
   → Receive: { "success": true, "profit": 90000, "roi_percent": 3.6 }

3. Plugin displays profit notification
4. Plugin requests next recommendation
```

### Example 6: User Has Active Trade

```
1. Plugin requests recommendation
   GET /recommendations?runescape_username=PlayerName&available_gp=5000000&available_ge_slots=4

2. Backend detects active trade
   → Receive: { "action": "wait", "active_trade": { ... } }

3. Plugin displays current trade status instead of new recommendation
```

---

## Error Handling

### HTTP Status Codes

| Code | Meaning | Common Causes |
|------|---------|---------------|
| 200 | Success | Request completed successfully |
| 201 | Created | Resource created successfully (e.g., trade, user) |
| 400 | Bad Request | Missing required fields or invalid data |
| 401 | Unauthorized | Invalid or expired JWT token |
| 403 | Forbidden | No active license or insufficient permissions |
| 404 | Not Found | Trade or resource doesn't exist |
| 409 | Conflict | Duplicate resource (e.g., email already exists) |
| 500 | Internal Server Error | Server-side error |
| 503 | Service Unavailable | ML system not ready or all items being traded |

### Recommended Error Handling Strategy

```java
// Pseudo-code example
try {
    Response response = apiClient.getRecommendations(...);
    
    if (response.status == 200) {
        // Process recommendation
    } else if (response.status == 503) {
        // Show "No recommendations available, try again later"
    } else if (response.status == 401) {
        // Token expired, prompt re-login
    } else if (response.status == 403) {
        // Show "No active license" message
    }
} catch (NetworkException e) {
    // Show "Cannot connect to server" message
}
```

---

## Rate Limiting

Currently no rate limiting is implemented, but plugins should:
- Not poll endpoints more frequently than once per 5 seconds
- Cache recommendation results for 30 seconds before requesting again
- Only update trades when actual GE state changes (not on a timer)

---

## Development Tips

### Testing Authentication
```bash
# Register a test user
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}'

# Login and get token
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}'
```

### Testing Recommendations
```bash
# Get recommendations (replace TOKEN with your JWT)
curl "http://localhost:8000/recommendations?runescape_username=TestPlayer&available_gp=5000000&available_ge_slots=4" \
  -H "Authorization: Bearer TOKEN"
```

### Testing Trade Creation
```bash
# Create trade (replace TOKEN and values)
curl -X POST http://localhost:8000/trades/create \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"item_id":4151,"buy_price":2500000,"buy_quantity":1,"sell_price":2580000}'
```

---

## Data Types Reference

### Trade Status Values
- `"buying"` - Buy order placed but not filled
- `"bought"` - Buy order completed, ready to sell
- `"selling"` - Sell order placed but not filled
- `"completed"` - Trade completed (handled by `/complete` endpoint)

### License Status Values
- `"active"` - License is valid and active
- `"expired"` - Subscription has expired
- `"suspended"` - License manually suspended

### Item ID Format
- Standard OSRS item IDs (e.g., 4151 for Abyssal whip)
- Plugin should maintain item name mapping or fetch from OSRS Wiki API

---

## Security Considerations

1. **Never log JWT tokens** - Store securely in RuneLite config
2. **Use HTTPS in production** - All API calls must use HTTPS
3. **Validate server certificates** - Don't allow self-signed certs in production
4. **Handle token expiration** - Prompt user to re-login when receiving 401
5. **Don't store passwords** - Only store JWT token after successful login

---

## Support

For API issues or questions:
- Check backend logs: `/logs/collector`, `/logs/hydra`, `/logs/postgres`
- Verify backend health: `GET /health`
- Check collector status: `GET /collector-status`

---

## Changelog

### Version 1.0 (2026-01-18)
- Initial API documentation
- Authentication endpoints
- Trade tracking endpoints
- Recommendation endpoint
- Complete trade workflow
