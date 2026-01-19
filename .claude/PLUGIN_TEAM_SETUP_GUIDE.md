# Arbitrage Pro - RuneLite Plugin Team Setup Guide

## Overview

You'll be developing a RuneLite plugin for **Arbitrage Pro**, an OSRS Grand Exchange arbitrage recommendation system. This plugin connects players to our backend API and helps them execute profitable GE trades automatically.

---

## Repository Setup

### Step 1: Create Separate Repository

Create a **new PUBLIC repository** called `arbitrage-pro` using the RuneLite plugin template:

```bash
# Clone RuneLite's example plugin template
git clone https://github.com/runelite/example-plugin.git arbitrage-pro-plugin
cd arbitrage-pro-plugin

# Remove template's git history
rm -rf .git

# Initialize fresh repository
git init
git add .
git commit -m "Initial commit from RuneLite template"

# Create repo on GitHub and push
git remote add origin https://github.com/YOUR_USERNAME/arbitrage-pro-plugin.git
git push -u origin main
```

### Step 2: Required Documentation Files

You should receive these files from the Arbitrage Pro team:

1. **PLUGIN_API_DOCUMENTATION.md** - Complete API reference with all endpoints
2. **RUNELITE_PLUGIN_SEPARATE_REPO_INSTRUCTIONS.md** - Detailed development tasks
3. **PLUGIN_TEAM_SETUP_GUIDE.md** - This file

Place these in your repository root or a `/docs` folder for reference.

---

## What You're Building

### Plugin Purpose
A RuneLite plugin that:
1. Authenticates users with Arbitrage Pro backend
2. Displays arbitrage recommendations in a sidebar panel
3. Automatically detects GE orders and tracks trades
4. Shows notifications for trade progress
5. Displays profit when trades complete

### Key Principle: Zero Extra Clicks
Users interact with the Grand Exchange normally. The plugin passively detects matching orders and handles everything in the background.

### Architecture
```
┌─────────────────┐         HTTPS         ┌──────────────────┐
│  RuneLite       │ ◄──────────────────►  │  Backend API     │
│  Plugin (You)   │   REST/JSON           │  (Arbitrage Pro) │
└─────────────────┘                       └──────────────────┘
       │
       ▼
   GE Events → Auto-detect → Report to backend
```

---

## Development Environment Setup

### Prerequisites

1. **Java JDK 11** (RuneLite requirement)
   ```bash
   java -version
   # Should show version 11.x.x
   ```

2. **IntelliJ IDEA** (Recommended) or Eclipse

3. **Maven** (Usually bundled with IDE)
   ```bash
   mvn -version
   ```

### Running the Plugin Locally

1. **Import project in IntelliJ**:
   - File → Open → Select `pom.xml`
   - Let Maven import dependencies

2. **Run RuneLite with your plugin**:
   ```bash
   mvn clean install
   mvn exec:java
   ```

3. **Enable plugin in RuneLite**:
   - Click wrench icon (Configuration)
   - Search for "Arbitrage Pro"
   - Toggle ON

### Testing Against Backend

#### Local Backend (Development)
```
Base URL: http://localhost:8000
```

#### Production Backend (When deployed)
```
Base URL: https://api.arbitragepro.com
```

Your plugin config should allow users to switch between these.

---

## Plugin Configuration

Users need to configure:

1. **API Endpoint** (default: production URL)
2. **Email/Password** (for authentication)
3. **RuneScape Username** (for license verification)

Store these securely in RuneLite's config system.

---

## Key Files to Create

Based on the template, you'll create:

### Core Plugin Files
```
src/main/java/com/arbitragepro/
├── ArbitrageProPlugin.java      # Main plugin class (extends Plugin)
├── ArbitrageProConfig.java      # Configuration interface
├── ArbitrageProPanel.java       # Sidebar panel UI
└── ArbitrageProOverlay.java     # Screen overlays/notifications
```

### API Client Package
```
src/main/java/com/arbitragepro/api/
├── ArbitrageProClient.java      # HTTP client wrapper (OkHttp)
├── AuthResponse.java            # Login response DTO
├── Recommendation.java          # Recommendation DTO
├── ActiveTrade.java             # Trade DTO
├── TradeCreateResponse.java     # Trade creation response
├── TradeUpdateResponse.java     # Trade update response
└── ApiException.java            # Custom exception
```

### Trade Tracking Package
```
src/main/java/com/arbitragepro/tracking/
├── TradeTracker.java            # GE order tracking logic
└── TradeState.java              # Local trade state
```

---

## Development Workflow

### Phase 1: Basic Structure (1-2 days)
- [ ] Set up plugin skeleton from template
- [ ] Create config interface
- [ ] Build basic sidebar panel UI
- [ ] Add plugin icon

### Phase 2: API Integration (2-3 days)
- [ ] Create HTTP client using OkHttp
- [ ] Implement authentication endpoints
- [ ] Implement recommendation endpoint
- [ ] Implement trade tracking endpoints
- [ ] Add error handling

### Phase 3: GE Event Detection (3-4 days)
- [ ] Subscribe to GrandExchangeOfferChanged events
- [ ] Validate buy orders against recommendations
- [ ] Validate sell orders against active trades
- [ ] Auto-create trades when valid orders detected
- [ ] Auto-update trades as orders progress

### Phase 4: UI & Polish (2-3 days)
- [ ] Display recommendations in panel
- [ ] Show active trade status
- [ ] Add overlays for notifications
- [ ] Add profit display
- [ ] Handle offline mode gracefully

### Phase 5: Testing & Refinement (2-3 days)
- [ ] Test full trade flow end-to-end
- [ ] Test error scenarios
- [ ] Test network failures
- [ ] Add unit tests
- [ ] Code cleanup

**Total Estimate**: 10-15 days

---

## API Quick Reference

### Authentication
```java
// Login
POST /auth/login
Body: { "email": "user@example.com", "password": "pass123" }
Response: { "token": "eyJ...", "user_id": 123, "licenses": [...] }

// Verify Token
GET /auth/verify
Header: Authorization: Bearer <token>
Response: { "valid": true, "license_status": "active" }
```

### Get Recommendation
```java
GET /recommendations?runescape_username=PlayerName&available_gp=5000000&available_ge_slots=4
Header: Authorization: Bearer <token>
Response: {
  "item_id": 4151,
  "item_name": "Abyssal whip",
  "buy_price": 2500000,
  "buy_quantity": 1,
  "sell_price": 2580000,
  "expected_profit": 80000
}
```

### Create Trade
```java
POST /trades/create
Header: Authorization: Bearer <token>
Body: {
  "item_id": 4151,
  "buy_price": 2500000,
  "buy_quantity": 1,
  "sell_price": 2580000,
  "recommendation_id": 789
}
Response: { "trade_id": 999, "status": "buying" }
```

### Update Trade
```java
POST /trades/999/update
Header: Authorization: Bearer <token>
Body: { "status": "bought", "buy_quantity_filled": 1 }
Response: { "success": true, "action": "sell", "sell_price": 2580000 }
```

### Complete Trade
```java
POST /trades/999/complete
Header: Authorization: Bearer <token>
Body: { "sell_quantity_filled": 1, "actual_sell_price": 2590000 }
Response: { "success": true, "profit": 90000, "roi_percent": 3.6 }
```

**Full API documentation is in PLUGIN_API_DOCUMENTATION.md**

---

## Testing Strategy

### Manual Testing Checklist
- [ ] Login with valid credentials
- [ ] Login with invalid credentials (should fail gracefully)
- [ ] Request recommendation with sufficient GP
- [ ] Request recommendation with insufficient GP
- [ ] Place matching buy order (should auto-create trade)
- [ ] Place non-matching buy order (should show warning)
- [ ] Complete buy order (should prompt to sell)
- [ ] Place sell order (should auto-update trade)
- [ ] Complete sell order (should show profit)
- [ ] Test with backend offline (should handle gracefully)

### Unit Tests
Create tests for:
- API client request/response parsing
- Trade state validation logic
- Price/item matching validation
- Error handling

---

## Validation Rules (CRITICAL)

### Buy Order Detection
```
✓ item_id == recommendation.item_id       [EXACT MATCH REQUIRED]
✓ price == recommendation.buy_price       [EXACT MATCH REQUIRED, not ±1gp]
✓ quantity <= recommendation.buy_quantity [User can buy LESS, not more]
```

### Sell Order Detection
```
✓ item_id == active_trade.item_id         [EXACT MATCH REQUIRED]
✓ price == active_trade.sell_price        [EXACT MATCH REQUIRED]
✓ quantity <= active_trade.quantity_held  [User can sell LESS, not more]
```

### On Mismatch
- **DO NOT** create or update trade
- Show warning overlay: "Price doesn't match recommendation"
- User must cancel and re-enter correct values

---

## Common Pitfalls to Avoid

1. **Don't implement business logic** - All logic is server-side, just display data
2. **Don't cache recommendations too long** - Max 30 seconds
3. **Don't poll aggressively** - Wait for GE events, don't poll every tick
4. **Don't ignore validation** - Strict price/item matching required
5. **Don't log sensitive data** - Never log passwords or tokens
6. **Don't allow self-signed certs in production** - Only for local dev

---

## RuneLite Submission Requirements

When ready to submit to RuneLite plugin hub:

1. **Open Source License** - Use BSD-2-Clause (required by RuneLite)
2. **Plugin Icon** - 128x128 PNG in resources
3. **README.md** - Clear description and setup instructions
4. **Clean Code** - Follow RuneLite code style
5. **No External Dependencies** - Only use RuneLite-approved libraries
6. **Testing** - Ensure it works in production RuneLite client

---

## Support & Communication

### Questions About Backend API
- Refer to PLUGIN_API_DOCUMENTATION.md
- Contact: [Provide your contact method]

### Questions About RuneLite Development
- RuneLite Discord: https://discord.gg/runelite
- RuneLite Plugin Hub: https://github.com/runelite/plugin-hub
- RuneLite Plugin Examples: https://github.com/runelite/example-plugin

### Reporting Issues
- Backend API issues: [Your issue tracker]
- Plugin bugs: Create issues in your plugin repository

---

## Resources

### Documentation
- **RuneLite API Docs**: https://static.runelite.net/api/runelite-api/
- **RuneLite Plugin Examples**: https://github.com/runelite?q=plugin
- **OSRS Wiki API**: https://oldschool.runescape.wiki/w/Application_programming_interface

### Libraries You'll Use
- **OkHttp**: HTTP client (already in RuneLite)
- **Gson**: JSON parsing (already in RuneLite)
- **Lombok**: Reduce boilerplate (already in RuneLite)

### Helpful Existing Plugins to Study
- **GE Plugin** (built-in) - For GE event handling
- **Price Check Plugin** - For item price display
- **Grand Exchange Plugin** - For offer tracking

---

## Next Steps

1. ✅ Set up repository from RuneLite template
2. ✅ Review PLUGIN_API_DOCUMENTATION.md thoroughly
3. ✅ Review RUNELITE_PLUGIN_SEPARATE_REPO_INSTRUCTIONS.md for detailed tasks
4. ✅ Set up development environment
5. ✅ Start with Task 1: Plugin Skeleton
6. ✅ Test against local backend
7. ✅ Implement features incrementally
8. ✅ Test thoroughly
9. ✅ Prepare for RuneLite submission

---

## Example Code Snippets

### Basic HTTP Request (OkHttp)
```java
OkHttpClient client = new OkHttpClient();

Request request = new Request.Builder()
    .url("http://localhost:8000/auth/login")
    .post(RequestBody.create(
        MediaType.parse("application/json"),
        "{\"email\":\"test@test.com\",\"password\":\"test123\"}"
    ))
    .build();

try (Response response = client.newCall(request).execute()) {
    if (response.isSuccessful()) {
        String json = response.body().string();
        // Parse with Gson
    }
}
```

### Subscribing to GE Events
```java
@Subscribe
public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
    GrandExchangeOffer offer = event.getOffer();
    
    if (offer.getState() == GrandExchangeOfferState.BOUGHT) {
        // Buy completed
        int itemId = offer.getItemId();
        int price = offer.getPrice();
        int quantity = offer.getTotalQuantity();
        
        // Validate and create trade
    }
}
```

### Creating Panel UI
```java
public class ArbitrageProPanel extends PluginPanel {
    private final JLabel itemNameLabel = new JLabel();
    private final JLabel buyPriceLabel = new JLabel();
    private final JLabel profitLabel = new JLabel();
    
    public ArbitrageProPanel() {
        setLayout(new BorderLayout());
        
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new GridLayout(0, 1));
        
        contentPanel.add(new JLabel("Item:"));
        contentPanel.add(itemNameLabel);
        contentPanel.add(new JLabel("Buy Price:"));
        contentPanel.add(buyPriceLabel);
        // ... etc
        
        add(contentPanel, BorderLayout.NORTH);
    }
    
    public void updateRecommendation(Recommendation rec) {
        itemNameLabel.setText(rec.getItemName());
        buyPriceLabel.setText(rec.getBuyPrice() + " gp");
    }
}
```

---

## Success Criteria

Your plugin is ready when:

- ✅ Users can log in through the plugin
- ✅ Recommendations display in sidebar panel
- ✅ Buy orders auto-create trades (with validation)
- ✅ Sell orders auto-update trades (with validation)
- ✅ Completed trades show profit
- ✅ Error messages are user-friendly
- ✅ Plugin handles offline/network failures gracefully
- ✅ Code follows RuneLite standards
- ✅ Full trade flow tested end-to-end

---

Good luck! This is a straightforward plugin with clear requirements. Focus on the GE event detection and API integration - those are the core features. The rest is UI polish.

**Remember**: When in doubt, refer to PLUGIN_API_DOCUMENTATION.md for exact API formats!
