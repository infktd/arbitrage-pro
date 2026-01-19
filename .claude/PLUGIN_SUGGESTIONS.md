# Plugin Team Recommendations
**From:** Backend/ML Team
**To:** RuneLite Plugin Team
**Date:** 2026-01-18
**Re:** Real-Time Price Validation Strategy

---

## Executive Summary

Our ML model achieves **93% accuracy** predicting profitable trades based on 5-minute averaged price data. However, there's a critical gap between **when recommendations are generated** and **when users act on them**:

**The Problem:**
- Hydra generates recommendations every 5 minutes using 5-minute averaged data
- Users see recommendations 0-5 minutes after generation (average: 2.5 min)
- By the time user places order, market conditions may have changed
- Bot dumps, panic sells, or manipulation can happen in seconds

**The Solution:**
Implement **real-time price validation** using the OSRS Wiki `/latest` endpoint before placing GE orders.

---

## Why This Matters: Real-World Scenarios

### Scenario 1: Bot Dump (Happens Frequently)
```
12:00 PM - Hydra generates recommendation:
  "Buy Dragon bones at 1,542gp, Sell at 1,576gp (2.2% ROI)"
  Based on 5-min avg: 11:55-12:00 data

12:02 PM - User sees recommendation in plugin, decides to act

12:03 PM - Bot dumps 50k Dragon bones on GE
  Instant-buy price spikes to 1,590gp

12:03:30 PM - User places buy order at 1,542gp (from recommendation)
  ❌ PROBLEM: Recommendation is now unprofitable!
  Buy: 1,590gp (actual) vs 1,542gp (expected)
  Sell: 1,576gp (unchanged)
  Loss: -14gp per bone (-0.88% ROI)
```

**Impact Without Validation:**
- User loses 14gp × GE limit (7,500) = **-105k loss**
- User trust in system decreases
- Negative reviews/feedback

**With /latest Validation:**
```
12:03:30 PM - Plugin calls /latest before placing order
  latest.low = 1,590gp (instant-buy price)

Plugin detects: 1,590 > 1,542 * 1.02 (2% tolerance)
Plugin shows: ⚠️ "Price spiked! Expected 1,542gp, now 1,590gp. Skip this trade?"
User: Clicks "Cancel" → Avoids 105k loss
```

---

### Scenario 2: Stale Recommendations (Low Liquidity)
```
10:00 AM - Hydra recommends:
  "Buy Teacher wand at 1,750k, Sell at 3M (71% ROI)"

10:03 AM - User tries to buy

/latest shows:
  lastTradeTime: 9:45 AM (18 minutes ago!)

Plugin warning: ⚠️ "No recent trades for 18 minutes - low liquidity item"
User: Understands this might take hours to fill, decides to skip
```

**Why This Happens:**
- 5-minute averages smooth out gaps
- Model can't detect if last trade was 2 min or 20 min ago
- `/latest` includes timestamps for each trade

---

### Scenario 3: Price Manipulation Detection
```
Recommendation: "Buy Xerician robe at 17,711gp"

/latest check reveals:
  latest.highTime: 11:58:23 AM
  latest.lowTime:  11:58:27 AM (4 seconds apart!)
  volume: Tiny (only 2 trades)

Plugin warning: ⚠️ "Suspicious price pattern detected"
```

**Interpretation:**
- Instant-buy/sell within 4 seconds on low-volume item
- Likely manipulation or price testing
- Not a safe trade

---

## Implementation Proposal

### Option A: Pre-Order Validation (Recommended) ⭐

**When:** Right before user places GE order

**Flow:**
```java
// In RuneLite plugin:
public void onRecommendationClick(Recommendation rec) {
    // 1. User clicks "Trade Now" on recommendation

    // 2. Plugin calls /latest for this item
    LatestPrice latest = osrsWikiAPI.getLatestPrice(rec.getItemId());

    // 3. Validate price hasn't moved significantly
    if (latest.getLow() > rec.getBuyPrice() * 1.02) {  // 2% tolerance
        showWarning(
            "Price increased!\n" +
            "Expected: " + rec.getBuyPrice() + "gp\n" +
            "Current: " + latest.getLow() + "gp\n" +
            "Recommendation may no longer be profitable."
        );
        return;  // Don't auto-fill order
    }

    // 4. Check liquidity (time since last trade)
    long minutesSinceLastTrade = (now() - latest.getLowTime()) / 60;
    if (minutesSinceLastTrade > 10) {
        showWarning(
            "Low liquidity - no trades in " + minutesSinceLastTrade + " minutes.\n" +
            "Order may take hours to fill."
        );
        // Still allow, but warn user
    }

    // 5. If all checks pass, proceed with order
    fillGEOrder(rec);
}
```

**API Call Frequency:**
- 1 call per recommendation the user actually acts on
- Typical user: 5-10 trades/day = 5-10 API calls/day
- Very respectful, won't get rate-limited

**Pros:**
- ✅ Catches price changes right before order placement
- ✅ Minimal API calls (only when user acts)
- ✅ Best user experience (prevents losses)
- ✅ Simple to implement

**Cons:**
- ⚠️ Adds ~200ms latency before placing order (negligible)

---

### Option B: Background Refresh (More Aggressive)

**When:** Every 30-60 seconds while recommendations are displayed

**Flow:**
```java
// Update displayed recommendations in real-time
private void refreshRecommendationPrices() {
    for (Recommendation rec : displayedRecommendations) {
        LatestPrice latest = osrsWikiAPI.getLatestPrice(rec.getItemId());

        if (latest.getLow() > rec.getBuyPrice() * 1.02) {
            rec.markAsStale();  // Gray out or remove from list
        }
    }
}

// Run every 60 seconds
scheduler.scheduleAtFixedRate(this::refreshRecommendationPrices, 60, SECONDS);
```

**API Call Frequency:**
- Up to 100 calls/minute (if user has 100 recommendations visible)
- Higher risk of rate limiting
- More aggressive, but more accurate

**Pros:**
- ✅ Recommendations auto-update in real-time
- ✅ User sees stale items grayed out immediately

**Cons:**
- ❌ Higher API usage (60-100x more calls)
- ❌ Risk of rate limiting
- ❌ More complex implementation

**Recommendation:** Don't do this - Option A is better

---

## Recommended Implementation: Option A Details

### API Endpoint
```
GET https://prices.runescape.wiki/api/v1/osrs/latest?id=<item_id>
```

**Response:**
```json
{
  "data": {
    "554": {  // Fire rune
      "high": 5,           // Last instant-sell price
      "highTime": 1737254891,  // Unix timestamp
      "low": 4,            // Last instant-buy price
      "lowTime": 1737254885
    }
  }
}
```

### Validation Logic

**1. Price Movement Check (Critical)**
```java
boolean isPriceValid(Recommendation rec, LatestPrice latest) {
    int expectedBuyPrice = rec.getBuyPrice();
    int actualBuyPrice = latest.getLow();  // Instant-buy price

    // Allow 2% slippage tolerance
    double tolerance = 1.02;

    if (actualBuyPrice > expectedBuyPrice * tolerance) {
        return false;  // Price moved up too much
    }

    // Also check sell side
    int expectedSellPrice = rec.getSellPrice();
    int actualSellPrice = latest.getHigh();

    if (actualSellPrice < expectedSellPrice * 0.98) {
        return false;  // Sell price dropped too much
    }

    return true;
}
```

**2. Liquidity Check (Important)**
```java
boolean hasRecentActivity(LatestPrice latest) {
    long currentTime = System.currentTimeMillis() / 1000;  // Unix timestamp
    long timeSinceLastBuy = currentTime - latest.getLowTime();
    long timeSinceLastSell = currentTime - latest.getHighTime();

    // Warn if no activity in last 10 minutes
    return timeSinceLastBuy < 600 && timeSinceLastSell < 600;
}
```

**3. User Notification Examples**

**When Price Moved:**
```
╔═══════════════════════════════════╗
║  ⚠️  Price Alert                  ║
╠═══════════════════════════════════╣
║  Dragon bones                     ║
║                                   ║
║  Expected buy: 1,542 gp           ║
║  Current buy:  1,590 gp (+3.1%)   ║
║                                   ║
║  This trade may no longer be      ║
║  profitable. Continue anyway?     ║
║                                   ║
║  [ Cancel ]  [ Trade Anyway ]     ║
╚═══════════════════════════════════╝
```

**When Low Liquidity:**
```
╔═══════════════════════════════════╗
║  ⚠️  Liquidity Warning             ║
╠═══════════════════════════════════╣
║  Teacher wand                     ║
║                                   ║
║  No trades in last 15 minutes     ║
║  This order may take hours to     ║
║  fill.                            ║
║                                   ║
║  [ Cancel ]  [ Continue ]         ║
╚═══════════════════════════════════╝
```

---

## Cost-Benefit Analysis

### Without /latest Validation

**User Experience:**
- Follows 10 recommendations per day
- 20% are stale/moved (2 trades)
- Average loss per bad trade: 50k
- **Monthly loss: 2 × 30 = 3M gp lost**
- User trust: Decreases over time
- Churn risk: High

### With /latest Validation

**User Experience:**
- Follows 10 recommendations per day
- 20% are caught and prevented (2 trades)
- **Monthly savings: 3M gp saved**
- User trust: High (system warns them proactively)
- Churn risk: Low

**API Cost:**
- 10 API calls per day per user
- Free, community-provided API
- Risk of rate limiting: Very low (respectful usage)

**Development Cost:**
- ~4-8 hours implementation
- ~2 hours testing
- Minimal maintenance

**ROI:** Extremely positive - prevents user losses, builds trust, minimal dev cost

---

## Technical Considerations

### Rate Limiting
The OSRS Wiki API doesn't publish official rate limits, but reasonable usage guidelines:

**Conservative Approach:**
- ✅ 1 call per user action (Option A)
- ✅ Include User-Agent header: `Arbitrage-Pro-Plugin/1.0`
- ✅ Cache results for 30 seconds per item
- ✅ Exponential backoff on errors

**What NOT to do:**
- ❌ Poll /latest continuously (Option B)
- ❌ Request bulk data (use /5m for that)
- ❌ Ignore 429 responses

### Error Handling

**If /latest API is down:**
```java
try {
    LatestPrice latest = osrsWikiAPI.getLatestPrice(itemId);
    if (!isPriceValid(rec, latest)) {
        showWarning("Price may have changed - proceed with caution");
        return;
    }
} catch (APIException e) {
    // Don't block user if API is down
    log.warn("Latest price check failed: " + e.getMessage());
    showTooltip("⚠️ Could not verify current price - trade at your own risk");
    // Still allow user to proceed
}
```

**Never block the user** if the validation API is unavailable.

### Caching Strategy

**Per-item cache (30 seconds):**
```java
private Map<Integer, CachedPrice> priceCache = new HashMap<>();

class CachedPrice {
    LatestPrice price;
    long timestamp;
}

LatestPrice getLatestPrice(int itemId) {
    CachedPrice cached = priceCache.get(itemId);
    if (cached != null && (now() - cached.timestamp) < 30_000) {
        return cached.price;  // Return cached value
    }

    // Fetch fresh data
    LatestPrice latest = apiClient.fetchLatest(itemId);
    priceCache.put(itemId, new CachedPrice(latest, now()));
    return latest;
}
```

**Why 30 seconds?**
- Balances freshness vs API calls
- User unlikely to click same item twice within 30sec
- If they do, cached value is still very fresh

---

## Alternative: What If We Don't Implement This?

**Risks:**
1. **User Losses:** Users will occasionally lose money on stale recommendations
2. **Trust Erosion:** "The bot told me to buy at X but price was Y"
3. **Negative Reviews:** "Recommendations are often wrong"
4. **Support Burden:** Users asking why recommendations didn't work
5. **Competitive Disadvantage:** Other arbitrage tools may have this feature

**Mitigation Without /latest:**
- Add disclaimer: "Prices may have changed since recommendation generated"
- Show recommendation timestamp: "Generated 3 minutes ago"
- Accept that 10-20% of trades will be unprofitable due to staleness

**But:** This doesn't solve the core problem, just shifts blame to the user.

---

## Recommendation Confidence Level

| Aspect | Confidence | Notes |
|--------|------------|-------|
| **Prevents User Losses** | ✅ Very High | Catches stale prices before orders |
| **Implementation Difficulty** | ✅ Low | ~1 day of work |
| **API Rate Limit Risk** | ✅ Very Low | Respectful usage pattern |
| **Maintenance Burden** | ✅ Very Low | Simple HTTP calls |
| **User Experience Improvement** | ✅ Very High | Proactive warnings build trust |
| **ROI** | ✅ Excellent | Low cost, high value |

**Overall Recommendation:** **Strongly Recommended** ⭐⭐⭐⭐⭐

This is a high-value, low-effort feature that significantly improves the user experience and prevents financial losses. The implementation is straightforward and the risks are minimal.

---

## Sample Implementation Pseudocode

```java
public class OSRSWikiAPIClient {
    private static final String LATEST_URL =
        "https://prices.runescape.wiki/api/v1/osrs/latest";

    private final Map<Integer, CachedLatestPrice> cache = new HashMap<>();

    public LatestPrice getLatestPrice(int itemId) throws APIException {
        // Check cache first
        CachedLatestPrice cached = cache.get(itemId);
        if (cached != null && cached.isValid()) {
            return cached.price;
        }

        // Fetch from API
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LATEST_URL + "?id=" + itemId))
                .header("User-Agent", "Arbitrage-Pro-Plugin/1.0")
                .build();

            HttpResponse<String> response = httpClient.send(request);

            if (response.statusCode() == 429) {
                throw new RateLimitException("Rate limited");
            }

            LatestPrice latest = parseLatestPrice(response.body(), itemId);
            cache.put(itemId, new CachedLatestPrice(latest, System.currentTimeMillis()));

            return latest;

        } catch (Exception e) {
            log.error("Failed to fetch latest price for item " + itemId, e);
            throw new APIException("Latest price unavailable", e);
        }
    }
}

public class RecommendationHandler {
    private final OSRSWikiAPIClient apiClient;

    public void onUserClickRecommendation(Recommendation rec) {
        try {
            // Validate price before placing order
            LatestPrice latest = apiClient.getLatestPrice(rec.getItemId());

            // Check buy price movement
            double buyPriceChange =
                (latest.getLow() - rec.getBuyPrice()) / (double) rec.getBuyPrice();

            if (buyPriceChange > 0.02) {  // More than 2% increase
                showPriceMovedDialog(rec, latest, buyPriceChange);
                return;  // Don't proceed
            }

            // Check sell price movement
            double sellPriceChange =
                (latest.getHigh() - rec.getSellPrice()) / (double) rec.getSellPrice();

            if (sellPriceChange < -0.02) {  // More than 2% decrease
                showPriceMovedDialog(rec, latest, sellPriceChange);
                return;
            }

            // Check liquidity
            long timeSinceLastTrade = System.currentTimeMillis() / 1000 - latest.getLowTime();
            if (timeSinceLastTrade > 600) {  // 10 minutes
                showLiquidityWarning(rec, timeSinceLastTrade / 60);
                // Still allow user to proceed, just warn
            }

            // All checks passed - proceed with order
            fillGrandExchangeOrder(rec);

        } catch (RateLimitException e) {
            log.warn("Rate limited, proceeding without validation");
            showTooltip("⚠️ Price validation unavailable - proceed with caution");
            // Don't block user

        } catch (APIException e) {
            log.error("Price validation failed", e);
            showTooltip("⚠️ Could not verify price - trade at your own risk");
            // Don't block user
        }
    }
}
```

---

## Questions?

**Q: What if the user wants to trade anyway despite warnings?**
A: Always allow them to proceed - we warn, we don't block. Include a "Trade Anyway" button.

**Q: Should we validate on every recommendation display, or only on click?**
A: Only on click (Option A). Validating on display (Option B) wastes API calls and risks rate limiting.

**Q: What if /latest shows null for a price?**
A: This means no one has traded that item recently. Show warning: "No recent trading activity - this item may be illiquid."

**Q: Should we cache /latest results?**
A: Yes, for 30 seconds per item. Balances freshness with API respect.

**Q: What tolerance should we use for price changes?**
A: Recommend ±2% - wide enough to handle normal fluctuation, tight enough to catch dumps/spikes.

**Q: Does this replace the ML model?**
A: No, this complements it. ML finds opportunities using 5-min data, /latest validates them before execution.

---

## Next Steps

1. **Prototype** basic /latest integration (1-2 hours)
2. **Test** with live data to calibrate tolerance thresholds
3. **User testing** to refine warning messages
4. **Deploy** behind feature flag for gradual rollout
5. **Monitor** API usage and user feedback

---

**Bottom Line:** This is a high-value feature that prevents user losses with minimal development cost. Strongly recommended for inclusion in v1.0.

**Contact:** Backend team for API endpoint details or ML model questions.
