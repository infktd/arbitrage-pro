package com.arbitragepro.api;

import lombok.Data;

/**
 * Latest real-time price data from OSRS Wiki API
 * Used for pre-order price validation
 */
@Data
public class LatestPrice {
    private final int itemId;
    private final int high;      // Last instant-sell price
    private final long highTime; // Unix timestamp of last sell
    private final int low;       // Last instant-buy price
    private final long lowTime;  // Unix timestamp of last buy

    /**
     * Check if this price data is recent (within last N minutes)
     */
    public boolean isRecent(int minutes) {
        long currentTime = System.currentTimeMillis() / 1000;
        long maxAge = minutes * 60;

        return (currentTime - lowTime < maxAge) && (currentTime - highTime < maxAge);
    }

    /**
     * Get time since last buy trade (in seconds)
     */
    public long getTimeSinceLastBuy() {
        return System.currentTimeMillis() / 1000 - lowTime;
    }

    /**
     * Get time since last sell trade (in seconds)
     */
    public long getTimeSinceLastSell() {
        return System.currentTimeMillis() / 1000 - highTime;
    }
}
