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

    // ================== GE EVENT DETECTION ==================

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        if (!config.autoTrack() || !isLoggedIn) {
            return;
        }

        GrandExchangeOffer offer = event.getOffer();
        if (offer == null) {
            return;
        }

        int itemId = offer.getItemId();
        int price = offer.getPrice();
        int quantity = offer.getTotalQuantity();
        int quantityFilled = offer.getQuantitySold();  // Bought or sold
        GrandExchangeOfferState state = offer.getState();

        log.debug("GE Event: itemId={}, price={}, qty={}, filled={}, state={}",
                itemId, price, quantity, quantityFilled, state);

        // Handle buy orders (creating new trades)
        if (currentRecommendation != null && activeTrade == null) {
            if (itemId == currentRecommendation.getItem_id() &&
                price == currentRecommendation.getBuy_price() &&
                quantity <= currentRecommendation.getBuy_quantity()) {

                if (state == GrandExchangeOfferState.BUYING) {
                    // User placed a buy order matching our recommendation
                    onBuyOrderPlaced(itemId, price, quantity);
                } else if (state == GrandExchangeOfferState.BOUGHT) {
                    // Buy order completed
                    onBuyOrderCompleted(itemId, quantityFilled);
                }
            }
        }

        // Handle sell orders (updating existing trades)
        if (activeTrade != null && activeTrade.getStatus().equals("bought")) {
            if (itemId == activeTrade.getItem_id() &&
                price == activeTrade.getSell_price()) {

                if (state == GrandExchangeOfferState.SELLING) {
                    // User placed sell order
                    log.info("Sell order placed for {}", activeTrade.getItem_name());
                } else if (state == GrandExchangeOfferState.SOLD) {
                    // Sell order completed
                    onSellOrderCompleted(itemId, quantityFilled);
                }
            }
        }
    }

    private void onBuyOrderPlaced(int itemId, int price, int quantity) {
        SwingUtilities.invokeLater(() -> {
            // Validate price before tracking trade
            if (currentRecommendation != null && config.validatePrices()) {
                log.info("Validating price for item {} before tracking...", itemId);
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

            // Create trade
            try {
                TradeCreateResponse response = apiClient.createTrade(itemId, price, quantity);
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
