package com.arbitragepro.api;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates recommendations against real-time prices
 * Prevents users from trading on stale/invalid recommendations
 */
@Slf4j
public class PriceValidator {
    private static final double BUY_PRICE_TOLERANCE = 1.02;  // 2% increase tolerance
    private static final double SELL_PRICE_TOLERANCE = 0.98; // 2% decrease tolerance
    private static final int LOW_LIQUIDITY_THRESHOLD_SECONDS = 600; // 10 minutes

    private final OSRSWikiAPIClient wikiClient;

    public PriceValidator(OSRSWikiAPIClient wikiClient) {
        this.wikiClient = wikiClient;
    }

    /**
     * Validate recommendation against latest real-time prices
     * Returns validation result with warnings/errors
     */
    public ValidationResult validate(Recommendation rec) {
        try {
            LatestPrice latest = wikiClient.getLatestPrice(rec.getItem_id());
            return performValidation(rec, latest);

        } catch (ApiException e) {
            log.warn("Failed to validate price for item {}: {}", rec.getItem_id(), e.getMessage());

            // Don't block user if API is unavailable
            return new ValidationResult(
                    ValidationStatus.API_UNAVAILABLE,
                    "Could not verify current price - trade at your own risk",
                    null
            );
        }
    }

    private ValidationResult performValidation(Recommendation rec, LatestPrice latest) {
        // 1. Check buy price movement
        double buyPriceChange = (double) (latest.getLow() - rec.getBuy_price()) / rec.getBuy_price();

        if (buyPriceChange > (BUY_PRICE_TOLERANCE - 1.0)) {
            double percentChange = buyPriceChange * 100;
            String message = String.format(
                    "Buy price increased %.1f%%\nExpected: %d gp\nCurrent: %d gp\n\nTrade may no longer be profitable.",
                    percentChange, rec.getBuy_price(), latest.getLow()
            );

            return new ValidationResult(ValidationStatus.PRICE_MOVED, message, latest);
        }

        // 2. Check sell price movement
        double sellPriceChange = (double) (latest.getHigh() - rec.getSell_price()) / rec.getSell_price();

        if (sellPriceChange < -(1.0 - SELL_PRICE_TOLERANCE)) {
            double percentChange = Math.abs(sellPriceChange * 100);
            String message = String.format(
                    "Sell price decreased %.1f%%\nExpected: %d gp\nCurrent: %d gp\n\nTrade may no longer be profitable.",
                    percentChange, rec.getSell_price(), latest.getHigh()
            );

            return new ValidationResult(ValidationStatus.PRICE_MOVED, message, latest);
        }

        // 3. Check liquidity (time since last trades)
        long timeSinceLastBuy = latest.getTimeSinceLastBuy();
        long timeSinceLastSell = latest.getTimeSinceLastSell();
        long maxTime = Math.max(timeSinceLastBuy, timeSinceLastSell);

        if (maxTime > LOW_LIQUIDITY_THRESHOLD_SECONDS) {
            long minutes = maxTime / 60;
            String message = String.format(
                    "Low liquidity warning\n\nNo trades in last %d minutes.\nOrder may take hours to fill.",
                    minutes
            );

            // Low liquidity is a warning, not a blocker
            return new ValidationResult(ValidationStatus.LOW_LIQUIDITY, message, latest);
        }

        // All checks passed
        log.info("Price validation passed for item {}", rec.getItem_id());
        return new ValidationResult(ValidationStatus.VALID, null, latest);
    }

    /**
     * Validation result
     */
    @Data
    public static class ValidationResult {
        private final ValidationStatus status;
        private final String message;
        private final LatestPrice latestPrice;

        public boolean isValid() {
            return status == ValidationStatus.VALID;
        }

        public boolean isWarning() {
            return status == ValidationStatus.LOW_LIQUIDITY || status == ValidationStatus.API_UNAVAILABLE;
        }

        public boolean isBlocking() {
            return status == ValidationStatus.PRICE_MOVED;
        }
    }

    /**
     * Validation status
     */
    public enum ValidationStatus {
        VALID,              // All checks passed
        PRICE_MOVED,        // Price moved significantly (blocking)
        LOW_LIQUIDITY,      // No recent trades (warning)
        API_UNAVAILABLE     // Could not verify (warning)
    }
}
