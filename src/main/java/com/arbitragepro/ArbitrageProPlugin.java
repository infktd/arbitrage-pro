package com.arbitragepro;

import com.arbitragepro.api.*;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@PluginDescriptor(
        name = "Arbitrage Pro",
        description = "GE arbitrage recommendations with ML predictions",
        tags = {"grand exchange", "trading", "arbitrage", "ge", "moneymaking"}
)
public class ArbitrageProPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ArbitrageProConfig config;

    private ArbitrageProClient apiClient;
    private OSRSWikiAPIClient wikiClient;
    private PriceValidator priceValidator;
    private ArbitrageProPanel panel;
    private NavigationButton navButton;

    // Current state
    private Recommendation currentRecommendation;
    private ActiveTrade activeTrade;
    private boolean isLoggedIn = false;

    // Offer state tracking (per slot)
    private final Map<Integer, OfferState> previousOfferStates = new HashMap<>();

    @Override
    protected void startUp() throws Exception {
        log.info("Arbitrage Pro plugin started");

        // Initialize API clients
        apiClient = new ArbitrageProClient(config.apiUrl());
        wikiClient = new OSRSWikiAPIClient();
        priceValidator = new PriceValidator(wikiClient);

        // Create panel
        panel = new ArbitrageProPanel(this);

        // Add navigation button
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Arbitrage Pro")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        // Auto-login if configured
        if (config.autoLogin() && !config.email().isEmpty() && !config.password().isEmpty()) {
            SwingUtilities.invokeLater(this::attemptLogin);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Arbitrage Pro plugin stopped");
        clientToolbar.removeNavigation(navButton);
    }

    @Provides
    ArbitrageProConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ArbitrageProConfig.class);
    }

    // ================== PUBLIC API FOR PANEL ==================

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public Recommendation getCurrentRecommendation() {
        return currentRecommendation;
    }

    public ActiveTrade getActiveTrade() {
        return activeTrade;
    }

    public void login(String email, String password) {
        SwingUtilities.invokeLater(() -> {
            try {
                apiClient.login(email, password);
                isLoggedIn = true;
                panel.onLoginSuccess();
                log.info("Login successful");

                // Fetch active trade if any
                fetchActiveTrade();
            } catch (ApiException e) {
                panel.onLoginError(e.getMessage());
                log.error("Login failed: {}", e.getMessage());
            }
        });
    }

    public void fetchRecommendation() {
        if (!isLoggedIn) {
            panel.showError("Not logged in");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                String username = config.runescapeUsername();
                if (username.isEmpty()) {
                    panel.showError("RuneScape username not configured");
                    return;
                }

                // Get current player's GP and available slots (simplified for now)
                int availableGp = 10_000_000;  // Could be enhanced to read actual GP
                int availableSlots = 8;

                currentRecommendation = apiClient.getRecommendation(username, availableGp, availableSlots);
                panel.displayRecommendation(currentRecommendation);
                log.info("Received recommendation: {} @ {}gp",
                        currentRecommendation.getItem_name(),
                        currentRecommendation.getBuy_price());

            } catch (ApiException e) {
                panel.showError("Failed to get recommendation: " + e.getMessage());
                log.error("Failed to fetch recommendation: {}", e.getMessage());
            }
        });
    }

    private void fetchActiveTrade() {
        SwingUtilities.invokeLater(() -> {
            try {
                activeTrade = apiClient.getActiveTrade();
                if (activeTrade != null) {
                    panel.displayActiveTrade(activeTrade);
                    log.info("Active trade found: {} @ {}gp",
                            activeTrade.getItem_name(),
                            activeTrade.getBuy_price());
                }
            } catch (ApiException e) {
                log.error("Failed to fetch active trade: {}", e.getMessage());
            }
        });
    }

    // ================== GE EVENT DETECTION (FlippingCopilot Pattern) ==================

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        if (!config.autoTrack() || !isLoggedIn) {
            return;
        }

        GrandExchangeOffer offer = event.getOffer();
        if (offer == null) {
            return;
        }

        int slot = event.getSlot();
        int itemId = offer.getItemId();
        int price = offer.getPrice();
        int totalQuantity = offer.getTotalQuantity();
        int quantityFilled = offer.getQuantitySold();
        int spent = offer.getSpent();
        GrandExchangeOfferState state = offer.getState();

        // Create current offer state snapshot
        OfferState currentState = new OfferState(
                slot, itemId, price, totalQuantity, quantityFilled, spent, state, System.currentTimeMillis()
        );

        // Get previous state for this slot
        OfferState previousState = previousOfferStates.get(slot);

        log.debug("GE Event [Slot {}]: itemId={}, price={}, qty={}, filled={}, spent={}, state={}",
                slot, itemId, price, totalQuantity, quantityFilled, spent, state);

        // Check if this is a new offer (different from previous)
        if (currentState.isNewOffer(previousState)) {
            log.debug("New offer detected in slot {}", slot);
            // This is a brand new offer - user just placed it
            handleNewOffer(currentState, previousState);
        } else if (previousState != null) {
            // Same offer, but state may have changed (partial fill, completion, etc.)
            handleOfferProgress(currentState, previousState);
        }

        // Save current state for next comparison
        previousOfferStates.put(slot, currentState);
    }

    /**
     * Handle brand new offer placement (user just clicked confirm in GE)
     */
    private void handleNewOffer(OfferState current, OfferState previous) {
        // Validate transition is consistent
        if (!current.isConsistentTransition(previous)) {
            log.warn("Inconsistent offer transition detected, ignoring");
            return;
        }

        // Check if this is a BUY order matching our recommendation
        if (current.isBuyOffer() && currentRecommendation != null && activeTrade == null) {
            if (current.getItemId() == currentRecommendation.getItem_id() &&
                current.getPrice() == currentRecommendation.getBuy_price() &&
                current.getTotalQuantity() <= currentRecommendation.getBuy_quantity()) {

                log.info("Buy order placed matching recommendation: {} @ {}gp",
                        currentRecommendation.getItem_name(), current.getPrice());

                // Create trade in backend
                createTradeForOffer(current);
            }
        }

        // Check if this is a SELL order for our active trade
        if (current.isSellOffer() && activeTrade != null && activeTrade.getStatus().equals("bought")) {
            if (current.getItemId() == activeTrade.getItem_id() &&
                current.getPrice() == activeTrade.getSell_price()) {

                log.info("Sell order placed for active trade: {}", activeTrade.getItem_name());
                // Backend will update on completion, nothing to do yet
            }
        }
    }

    /**
     * Handle progress on existing offer (partial fills, completion)
     */
    private void handleOfferProgress(OfferState current, OfferState previous) {
        int quantityDiff = current.getQuantitySold() - previous.getQuantitySold();
        int spentDiff = current.getSpent() - previous.getSpent();

        if (quantityDiff <= 0 && spentDiff <= 0) {
            // No progress made
            return;
        }

        log.debug("Offer progress: +{} quantity, +{} gp spent", quantityDiff, spentDiff);

        // Check for buy order completion
        if (current.getState() == GrandExchangeOfferState.BOUGHT &&
            previous.getState() == GrandExchangeOfferState.BUYING) {

            if (activeTrade != null && current.getItemId() == activeTrade.getItem_id()) {
                log.info("Buy order completed: {} x{}", activeTrade.getItem_name(), current.getQuantitySold());
                onBuyOrderCompleted(current.getItemId(), current.getQuantitySold());
            }
        }

        // Check for sell order completion
        if (current.getState() == GrandExchangeOfferState.SOLD &&
            previous.getState() == GrandExchangeOfferState.SELLING) {

            if (activeTrade != null && current.getItemId() == activeTrade.getItem_id()) {
                log.info("Sell order completed: {} x{}", activeTrade.getItem_name(), current.getQuantitySold());
                onSellOrderCompleted(current.getItemId(), current.getQuantitySold());
            }
        }
    }

    /**
     * Create trade in backend when user places matching GE order (FlippingCopilot pattern)
     */
    private void createTradeForOffer(OfferState offer) {
        SwingUtilities.invokeLater(() -> {
            // Validate price before tracking trade
            if (currentRecommendation != null && config.validatePrices()) {
                log.info("Validating price for item {} before tracking...", offer.getItemId());
                PriceValidator.ValidationResult validation = priceValidator.validate(currentRecommendation);

                // Handle validation result
                if (validation.isBlocking()) {
                    // Price moved significantly - warn user and don't track
                    log.warn("Price validation failed: {}", validation.getMessage());
                    boolean proceed = panel.showPriceMovedWarning(validation.getMessage() +
                            "\n\nNote: Your GE order is still active.\nYou may want to cancel it.");

                    if (!proceed) {
                        log.info("User declined to proceed with stale recommendation");
                        currentRecommendation = null;  // Clear recommendation
                        panel.clearRecommendation();
                        return;  // Don't create trade
                    }
                    // If user chooses to proceed anyway, continue below
                }

                if (validation.isWarning()) {
                    // Show warning but don't block
                    if (validation.getStatus() == PriceValidator.ValidationStatus.LOW_LIQUIDITY) {
                        panel.showLiquidityWarning(validation.getMessage());
                    } else if (validation.getStatus() == PriceValidator.ValidationStatus.API_UNAVAILABLE) {
                        panel.showAPIUnavailableWarning(validation.getMessage());
                    }
                    // Continue with trade creation
                }
            }

            // Create trade in backend
            try {
                TradeCreateResponse response = apiClient.createTrade(
                        offer.getItemId(),
                        offer.getPrice(),
                        offer.getTotalQuantity()
                );
                log.info("Trade created: ID={}", response.getTrade_id());

                // Fetch updated active trade
                fetchActiveTrade();
                currentRecommendation = null;  // Clear recommendation
                panel.clearRecommendation();

            } catch (ApiException e) {
                log.error("Failed to create trade: {}", e.getMessage());
                panel.showError("Failed to track trade: " + e.getMessage());
            }
        });
    }

    private void onBuyOrderCompleted(int itemId, int quantityFilled) {
        if (activeTrade == null) return;

        SwingUtilities.invokeLater(() -> {
            try {
                TradeUpdateResponse response = apiClient.updateTrade(
                        activeTrade.getTrade_id(),
                        "bought",
                        quantityFilled
                );

                log.info("Trade updated to bought: {}", response.getAction());
                fetchActiveTrade();

                if (config.showNotifications()) {
                    panel.showNotification("Buy order complete! Now sell at " + response.getSell_price() + "gp");
                }

            } catch (ApiException e) {
                log.error("Failed to update trade: {}", e.getMessage());
            }
        });
    }

    private void onSellOrderCompleted(int itemId, int quantityFilled) {
        if (activeTrade == null) return;

        SwingUtilities.invokeLater(() -> {
            try {
                TradeUpdateResponse response = apiClient.updateTrade(
                        activeTrade.getTrade_id(),
                        "completed",
                        quantityFilled
                );

                log.info("Trade completed!");
                activeTrade = null;
                panel.clearActiveTrade();

                if (config.showNotifications()) {
                    panel.showNotification("Trade completed! Nice profit!");
                }

            } catch (ApiException e) {
                log.error("Failed to complete trade: {}", e.getMessage());
            }
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            log.info("Player logged in to game");
        }
    }

    private void attemptLogin() {
        if (!config.email().isEmpty() && !config.password().isEmpty()) {
            login(config.email(), config.password());
        }
    }
}
