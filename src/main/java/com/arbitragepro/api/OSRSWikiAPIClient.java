package com.arbitragepro.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Client for OSRS Wiki Prices API
 * Fetches real-time price data for validation
 */
@Slf4j
public class OSRSWikiAPIClient {
    private static final String LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";
    private static final int CACHE_TTL_SECONDS = 30;

    private final OkHttpClient client;
    private final Map<Integer, CachedLatestPrice> cache;

    public OSRSWikiAPIClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        this.cache = new HashMap<>();
    }

    /**
     * Get latest price for an item (with 30-second cache)
     */
    public LatestPrice getLatestPrice(int itemId) throws ApiException {
        // Check cache first
        CachedLatestPrice cached = cache.get(itemId);
        if (cached != null && cached.isValid()) {
            log.debug("Cache hit for item {}", itemId);
            return cached.price;
        }

        // Fetch from API
        try {
            Request request = new Request.Builder()
                    .url(LATEST_URL + "?id=" + itemId)
                    .header("User-Agent", "Arbitrage-Pro-Plugin/1.0 (contact: your@email.com)")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    if (response.code() == 429) {
                        throw new ApiException("Rate limited by OSRS Wiki API", 429);
                    }
                    throw new ApiException("Failed to fetch latest price: HTTP " + response.code(), response.code());
                }

                String body = response.body().string();
                LatestPrice latest = parseLatestPrice(body, itemId);

                // Cache result
                cache.put(itemId, new CachedLatestPrice(latest, System.currentTimeMillis()));
                log.debug("Fetched latest price for item {}: buy={}gp, sell={}gp",
                        itemId, latest.getLow(), latest.getHigh());

                return latest;
            }

        } catch (IOException e) {
            log.error("Network error fetching latest price for item {}", itemId, e);
            throw new ApiException("Network error: " + e.getMessage(), 0);
        }
    }

    /**
     * Parse JSON response from /latest endpoint
     */
    private LatestPrice parseLatestPrice(String json, int itemId) throws ApiException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");

            if (data == null || !data.has(String.valueOf(itemId))) {
                throw new ApiException("No price data available for item " + itemId, 0);
            }

            JsonObject itemData = data.getAsJsonObject(String.valueOf(itemId));

            // Extract price data (may be null if no recent trades)
            Integer high = getIntOrNull(itemData, "high");
            Long highTime = getLongOrNull(itemData, "highTime");
            Integer low = getIntOrNull(itemData, "low");
            Long lowTime = getLongOrNull(itemData, "lowTime");

            // If any required field is null, item has no recent trading activity
            if (high == null || low == null || highTime == null || lowTime == null) {
                throw new ApiException("No recent trading activity for item " + itemId, 0);
            }

            return new LatestPrice(itemId, high, highTime, low, lowTime);

        } catch (Exception e) {
            log.error("Failed to parse latest price response", e);
            throw new ApiException("Invalid API response: " + e.getMessage(), 0);
        }
    }

    private Integer getIntOrNull(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return (element != null && !element.isJsonNull()) ? element.getAsInt() : null;
    }

    private Long getLongOrNull(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return (element != null && !element.isJsonNull()) ? element.getAsLong() : null;
    }

    /**
     * Clear cache (useful for testing)
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Cached price with TTL
     */
    private static class CachedLatestPrice {
        final LatestPrice price;
        final long timestamp;

        CachedLatestPrice(LatestPrice price, long timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }

        boolean isValid() {
            long age = System.currentTimeMillis() - timestamp;
            return age < CACHE_TTL_SECONDS * 1000;
        }
    }
}
