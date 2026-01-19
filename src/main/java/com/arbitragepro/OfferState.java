package com.arbitragepro;

import lombok.Data;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Snapshot of a GE offer's state at a point in time
 * Used to detect state changes and infer transactions
 */
@Data
public class OfferState {
    private final int slot;
    private final int itemId;
    private final int price;
    private final int totalQuantity;
    private final int quantitySold;  // Current progress
    private final int spent;         // GP spent so far
    private final GrandExchangeOfferState state;
    private final long timestamp;

    /**
     * Check if this represents a buy order
     */
    public boolean isBuyOffer() {
        return state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT;
    }

    /**
     * Check if this represents a sell order
     */
    public boolean isSellOffer() {
        return state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.SOLD;
    }

    /**
     * Check if offer is completed
     */
    public boolean isCompleted() {
        return state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD;
    }

    /**
     * Check if this is a new/different offer compared to previous
     */
    public boolean isNewOffer(OfferState previous) {
        if (previous == null) {
            return true;
        }

        // Different if any of these changed
        return itemId != previous.itemId ||
               price != previous.price ||
               totalQuantity != previous.totalQuantity ||
               quantitySold < previous.quantitySold;  // Quantity decreased = new offer
    }

    /**
     * Check if transition from previous state is consistent/valid
     */
    public boolean isConsistentTransition(OfferState previous) {
        if (previous == null) {
            return true;  // First time seeing this offer
        }

        // Item and price should never change mid-offer
        if (itemId != previous.itemId || price != previous.price) {
            return false;
        }

        // Quantity and spending should only increase (or stay same)
        if (quantitySold < previous.quantitySold || spent < previous.spent) {
            return false;
        }

        return true;
    }
}
