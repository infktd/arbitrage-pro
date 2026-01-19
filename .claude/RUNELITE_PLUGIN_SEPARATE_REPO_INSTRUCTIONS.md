# RuneLite Plugin Development Instructions (Separate Repository)

## Recommended Model: Claude Sonnet 4

## ⚠️ IMPORTANT: API Documentation

**ALL API endpoints, request/response formats, and authentication details are documented in:**
`PLUGIN_API_DOCUMENTATION.md`

This file must be provided to the plugin development team along with these instructions.

## Repository Setup

This plugin should be developed in a **separate public repository** from the main Arbitrage Pro server codebase. This maintains the required open-source nature for RuneLite plugin submission while keeping the server-side ML models and business logic private.

### Why Separate Repository?

1. **Open Source Requirement**: RuneLite requires all plugins to be open source
2. **IP Protection**: Keeps ML models and business logic private in main repo
3. **Clean Dependencies**: Plugin only depends on RuneLite APIs, not backend code
4. **Easier Submission**: Follows RuneLite's plugin template structure exactly

### Repository Structure
```
arbitrage-pro-plugin/ (NEW SEPARATE REPO - PUBLIC)
├── README.md
├── LICENSE
├── pom.xml
├── src/main/java/com/arbitragepro/
│   ├── ArbitrageProPlugin.java      # Main plugin class
│   ├── ArbitrageProConfig.java      # Configuration interface
│   ├── ArbitrageProPanel.java       # Sidebar panel UI
│   ├── ArbitrageProOverlay.java     # Screen overlays/notifications
│   ├── api/
│   │   ├── ArbitrageProClient.java  # HTTP client wrapper
│   │   ├── AuthResponse.java        # Auth response DTO
│   │   ├── Recommendation.java      # Recommendation DTO
│   │   ├── ActiveTrade.java         # Trade DTO
│   │   ├── TradeCreateResponse.java # Trade creation response
│   │   ├── TradeUpdateResponse.java # Trade update response
│   │   └── ApiException.java        # Custom exception
│   └── tracking/
│       ├── TradeTracker.java        # GE order tracking logic
│       └── TradeState.java          # Local trade state
└── src/main/resources/
    └── com/arbitragepro/
        └── plugin-icon.png
```

## Project Context

You are building a **RuneLite plugin** for **Arbitrage Pro** - a system that provides OSRS Grand Exchange arbitrage recommendations. The plugin authenticates with our backend, displays recommendations, and **automatically detects** when users place GE orders to create/track trades.

### Key Design Principle
**Zero extra clicks** - User just interacts with the GE normally. Plugin passively detects matching orders and reports to backend.

### Architecture Overview
```
RuneLite Plugin (YOU) ←→ Backend API (REST/HTTPS)
       ↓
   GE Events → Auto-detect orders → Report to backend
```

### Plugin Scope (Keep Minimal)
- **HTTP client** to communicate with backend API
- **UI panel** to display recommendations  
- **User authentication/session management**
- **GE order detection** with strict validation
- **Settings/configuration panel**
- **Error handling and offline mode**
- **Overlay notifications**

### What NOT to Include
- No business logic (all logic is server-side)
- No ML models or feature engineering
- No database connections
- No complex calculations (just display data from API)

---

## Plugin Requirements

### Validation Rules (STRICT)
```
BUY ORDER VALIDATION:
✓ item_id == recommendation.item_id       → REQUIRED (exact match)
✓ price == recommendation.buy_price       → REQUIRED (exact match, not ±1gp)
✓ quantity <= recommendation.buy_quantity → ALLOWED (user can buy less)

SELL ORDER VALIDATION:
✓ item_id == active_trade.item_id         → REQUIRED (exact match)
✓ price == active_trade.sell_price        → REQUIRED (exact match)
✓ quantity <= active_trade.quantity_held  → ALLOWED (user can sell less)

ON MISMATCH:
→ Do NOT create/update trade
→ Show overlay warning: "Price doesn't match recommendation" or "Item doesn't match"
→ User must cancel and re-enter correct values
```

---

## Your Tasks

### Task 1: Plugin Skeleton
**Commit Point: "feat: create plugin skeleton"**

Create the basic RuneLite plugin structure:

**pom.xml:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.arbitragepro</groupId>
    <artifactId>arbitrage-pro</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>Arbitrage Pro</name>
    <description>GE arbitrage recommendations with ML predictions</description>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <runelite.version>1.10.25</runelite.version>
    </properties>

    <repositories>
        <repository>
            <id>runelite</id>
            <url>https://repo.runelite.net</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>net.runelite</groupId>
            <artifactId>client</artifactId>
            <version>${runelite.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.30</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.10.1</version>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>4.12.0</version>
        </dependency>
    </dependencies>
</project>
```

**ArbitrageProPlugin.java:**
```java
package com.arbitragepro;

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
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
    name = "Arbitrage Pro",
    description = "GE arbitrage recommendations with ML predictions",
    tags = {"ge", "grand exchange", "arbitrage", "flip", "profit"}
)
public class ArbitrageProPlugin extends Plugin {
    
    @Inject
    private Client client;
    
    @Inject
    private ArbitrageProConfig config;
    
    @Inject
    private ClientToolbar clientToolbar;
    
    @Inject
    private OverlayManager overlayManager;
    
    private ArbitrageProPanel panel;
    private ArbitrageProOverlay overlay;
    private NavigationButton navButton;
    
    @Inject
    private TradeTracker tradeTracker;
    
    @Override
    protected void startUp() throws Exception {
        log.info("Arbitrage Pro starting up");
        
        panel = injector.getInstance(ArbitrageProPanel.class);
        overlay = injector.getInstance(ArbitrageProOverlay.class);
        
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/plugin-icon.png");
        
        navButton = NavigationButton.builder()
            .tooltip("Arbitrage Pro")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();
        
        clientToolbar.addNavigation(navButton);
        overlayManager.add(overlay);
    }
    
    @Override
    protected void shutDown() throws Exception {
        log.info("Arbitrage Pro shutting down");
        clientToolbar.removeNavigation(navButton);
        overlayManager.remove(overlay);
    }
    
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            // Player logged in - check auth status
            panel.onPlayerLoggedIn();
        } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            // Player logged out
            panel.onPlayerLoggedOut();
        }
    }
    
    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        // This is the KEY event for auto-detecting GE orders
        GrandExchangeOffer offer = event.getOffer();
        int slot = event.getSlot();
        
        log.debug("GE offer changed - Slot: {}, Item: {}, State: {}, Price: {}, Qty: {}/{}",
            slot,
            offer.getItemId(),
            offer.getState(),
            offer.getPrice(),
            offer.getQuantitySold(),
            offer.getTotalQuantity()
        );
        
        tradeTracker.handleOfferChanged(slot, offer);
    }
    
    @Provides
    ArbitrageProConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ArbitrageProConfig.class);
    }
}
```

**ArbitrageProConfig.java:**
```java
package com.arbitragepro;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("arbitragepro")
public interface ArbitrageProConfig extends Config {
    
    @ConfigSection(
        name = "Server Settings",
        description = "Backend server configuration",
        position = 0
    )
    String serverSection = "server";
    
    @ConfigItem(
        keyName = "serverUrl",
        name = "Server URL",
        description = "Arbitrage Pro backend URL",
        section = serverSection,
        position = 0
    )
    default String serverUrl() {
        return "https://api.arbitragepro.gg";
    }
    
    @ConfigSection(
        name = "Authentication",
        description = "Login credentials",
        position = 1
    )
    String authSection = "auth";
    
    @ConfigItem(
        keyName = "email",
        name = "Email",
        description = "Your Arbitrage Pro account email",
        section = authSection,
        position = 0
    )
    default String email() {
        return "";
    }
    
    @ConfigItem(
        keyName = "jwtToken",
        name = "Auth Token",
        description = "JWT token (auto-populated after login)",
        section = authSection,
        position = 1,
        hidden = true
    )
    default String jwtToken() {
        return "";
    }
    
    @ConfigSection(
        name = "Notifications",
        description = "Notification settings",
        position = 2
    )
    String notifSection = "notifications";
    
    @ConfigItem(
        keyName = "showOverlays",
        name = "Show Overlays",
        description = "Show on-screen notifications",
        section = notifSection,
        position = 0
    )
    default boolean showOverlays() {
        return true;
    }
    
    @ConfigItem(
        keyName = "notifyOnNewRec",
        name = "New Recommendation Alert",
        description = "Notify when new recommendation is available",
        section = notifSection,
        position = 1
    )
    default boolean notifyOnNewRec() {
        return true;
    }
}
```

---

### Task 2: Authentication Flow
**Commit Point: "feat: implement authentication"**

**api/ArbitrageProClient.java:**
```java
package com.arbitragepro.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class ArbitrageProClient {
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    private String baseUrl;
    private String jwtToken;
    
    @Inject
    public ArbitrageProClient() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.gson = new GsonBuilder().create();
    }
    
    public void configure(String baseUrl, String jwtToken) {
        this.baseUrl = baseUrl;
        this.jwtToken = jwtToken;
    }
    
    public void setToken(String token) {
        this.jwtToken = token;
    }
    
    // === Authentication ===
    
    public CompletableFuture<AuthResponse> login(String email, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = gson.toJson(new LoginRequest(email, password));
                RequestBody body = RequestBody.create(json, JSON);
                
                Request request = new Request.Builder()
                    .url(baseUrl + "/auth/login")
                    .post(body)
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new ApiException("Login failed: " + response.code());
                    }
                    return gson.fromJson(response.body().string(), AuthResponse.class);
                }
            } catch (IOException e) {
                throw new RuntimeException("Network error during login", e);
            }
        });
    }
    
    public CompletableFuture<Boolean> verifyToken() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Request request = new Request.Builder()
                    .url(baseUrl + "/auth/verify")
                    .header("Authorization", "Bearer " + jwtToken)
                    .get()
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    return response.isSuccessful();
                }
            } catch (IOException e) {
                log.error("Token verification failed", e);
                return false;
            }
        });
    }
    
    // === Recommendations ===
    
    public CompletableFuture<Recommendation> getRecommendation(
            String runescapeUsername, 
            long availableGp, 
            int availableGeSlots) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpUrl url = HttpUrl.parse(baseUrl + "/recommendations")
                    .newBuilder()
                    .addQueryParameter("runescape_username", runescapeUsername)
                    .addQueryParameter("available_gp", String.valueOf(availableGp))
                    .addQueryParameter("available_ge_slots", String.valueOf(availableGeSlots))
                    .build();
                
                Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + jwtToken)
                    .get()
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new ApiException("Failed to get recommendation: " + response.code());
                    }
                    return gson.fromJson(response.body().string(), Recommendation.class);
                }
            } catch (IOException e) {
                throw new RuntimeException("Network error getting recommendation", e);
            }
        });
    }
    
    // === Trade Tracking ===
    
    public CompletableFuture<TradeCreateResponse> createTrade(
            int itemId, 
            int buyPrice, 
            int buyQuantity,
            Integer recommendationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TradeCreateRequest req = new TradeCreateRequest(
                    itemId, buyPrice, buyQuantity, recommendationId
                );
                String json = gson.toJson(req);
                RequestBody body = RequestBody.create(json, JSON);
                
                Request request = new Request.Builder()
                    .url(baseUrl + "/trades/create")
                    .header("Authorization", "Bearer " + jwtToken)
                    .post(body)
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new ApiException("Failed to create trade: " + response.code());
                    }
                    return gson.fromJson(response.body().string(), TradeCreateResponse.class);
                }
            } catch (IOException e) {
                throw new RuntimeException("Network error creating trade", e);
            }
        });
    }
    
    public CompletableFuture<TradeUpdateResponse> updateTrade(
            int tradeId,
            String status,
            Integer quantityFilled) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TradeUpdateRequest req = new TradeUpdateRequest(status, quantityFilled);
                String json = gson.toJson(req);
                RequestBody body = RequestBody.create(json, JSON);
                
                Request request = new Request.Builder()
                    .url(baseUrl + "/trades/" + tradeId + "/update")
                    .header("Authorization", "Bearer " + jwtToken)
                    .post(body)
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new ApiException("Failed to update trade: " + response.code());
                    }
                    return gson.fromJson(response.body().string(), TradeUpdateResponse.class);
                }
            } catch (IOException e) {
                throw new RuntimeException("Network error updating trade", e);
            }
        });
    }
    
    public CompletableFuture<ActiveTrade[]> getActiveTrades() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Request request = new Request.Builder()
                    .url(baseUrl + "/trades/active")
                    .header("Authorization", "Bearer " + jwtToken)
                    .get()
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new ApiException("Failed to get active trades: " + response.code());
                    }
                    ActiveTradesResponse resp = gson.fromJson(
                        response.body().string(), 
                        ActiveTradesResponse.class
                    );
                    return resp.trades;
                }
            } catch (IOException e) {
                throw new RuntimeException("Network error getting active trades", e);
            }
        });
    }
    
    // Inner classes for request/response DTOs
    private static class LoginRequest {
        String email;
        String password;
        LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }
    
    private static class TradeCreateRequest {
        int item_id;
        int buy_price;
        int buy_quantity;
        Integer recommendation_id;
        TradeCreateRequest(int itemId, int buyPrice, int buyQty, Integer recId) {
            this.item_id = itemId;
            this.buy_price = buyPrice;
            this.buy_quantity = buyQty;
            this.recommendation_id = recId;
        }
    }
    
    private static class TradeUpdateRequest {
        String status;
        Integer quantity_filled;
        TradeUpdateRequest(String status, Integer qty) {
            this.status = status;
            this.quantity_filled = qty;
        }
    }
    
    private static class ActiveTradesResponse {
        ActiveTrade[] trades;
    }
}
```

**api/AuthResponse.java:**
```java
package com.arbitragepro.api;

import lombok.Data;

@Data
public class AuthResponse {
    private int user_id;
    private String token;
    private License[] licenses;
    
    @Data
    public static class License {
        private int license_id;
        private String runescape_username;
        private String status;
        private String subscription_end;
    }
}
```

**api/ApiException.java:**
```java
package com.arbitragepro.api;

public class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }
    
    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

### Task 3: Recommendation Display Panel
**Commit Point: "feat: add recommendation panel"**

**ArbitrageProPanel.java:**
```java
package com.arbitragepro;

import com.arbitragepro.api.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.PluginErrorPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Slf4j
public class ArbitrageProPanel extends PluginPanel {
    
    private final ArbitrageProConfig config;
    private final ArbitrageProClient client;
    
    // Login components
    private JPanel loginPanel;
    private FlatTextField emailField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel loginStatusLabel;
    
    // Main content components
    private JPanel mainPanel;
    private JPanel recommendationPanel;
    private JLabel itemNameLabel;
    private JLabel buyPriceLabel;
    private JLabel buyQuantityLabel;
    private JLabel sellPriceLabel;
    private JLabel expectedProfitLabel;
    private JLabel statusLabel;
    
    // State
    private boolean isAuthenticated = false;
    private Recommendation currentRecommendation;
    
    @Inject
    public ArbitrageProPanel(ArbitrageProConfig config, ArbitrageProClient client) {
        this.config = config;
        this.client = client;
        
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        buildLoginPanel();
        buildMainPanel();
        
        // Start with login panel
        add(loginPanel, BorderLayout.CENTER);
        
        // Check existing token
        if (!config.jwtToken().isEmpty()) {
            checkExistingAuth();
        }
    }
    
    private void buildLoginPanel() {
        loginPanel = new JPanel();
        loginPanel.setLayout(new BoxLayout(loginPanel, BoxLayout.Y_AXIS));
        loginPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        JLabel titleLabel = new JLabel("Arbitrage Pro");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel subtitleLabel = new JLabel("Login to continue");
        subtitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        emailField = new FlatTextField();
        emailField.setPreferredSize(new Dimension(200, 30));
        emailField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        emailField.setText(config.email());
        
        JLabel emailLabel = new JLabel("Email");
        emailLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        
        passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(200, 30));
        passwordField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        
        loginButton = new JButton("Login");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.addActionListener(e -> doLogin());
        
        loginStatusLabel = new JLabel(" ");
        loginStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loginStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        loginPanel.add(Box.createVerticalGlue());
        loginPanel.add(titleLabel);
        loginPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        loginPanel.add(subtitleLabel);
        loginPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        loginPanel.add(emailLabel);
        loginPanel.add(emailField);
        loginPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        loginPanel.add(passwordLabel);
        loginPanel.add(passwordField);
        loginPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        loginPanel.add(loginButton);
        loginPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        loginPanel.add(loginStatusLabel);
        loginPanel.add(Box.createVerticalGlue());
    }
    
    private void buildMainPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel headerLabel = new JLabel("Current Recommendation");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerLabel.setForeground(Color.WHITE);
        headerPanel.add(headerLabel, BorderLayout.WEST);
        
        JButton refreshButton = new JButton("↻");
        refreshButton.setToolTipText("Refresh recommendation");
        refreshButton.addActionListener(e -> fetchRecommendation());
        headerPanel.add(refreshButton, BorderLayout.EAST);
        
        // Recommendation display
        recommendationPanel = new JPanel();
        recommendationPanel.setLayout(new BoxLayout(recommendationPanel, BoxLayout.Y_AXIS));
        recommendationPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        recommendationPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        itemNameLabel = new JLabel("--");
        itemNameLabel.setFont(itemNameLabel.getFont().deriveFont(Font.BOLD, 16f));
        itemNameLabel.setForeground(Color.WHITE);
        
        buyPriceLabel = new JLabel("Buy: --");
        buyPriceLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        
        buyQuantityLabel = new JLabel("Quantity: --");
        buyQuantityLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        
        sellPriceLabel = new JLabel("Sell: --");
        sellPriceLabel.setForeground(new Color(255, 152, 0)); // Orange
        
        expectedProfitLabel = new JLabel("Expected Profit: --");
        expectedProfitLabel.setForeground(Color.GREEN);
        
        statusLabel = new JLabel("Waiting for login...");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        recommendationPanel.add(itemNameLabel);
        recommendationPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        recommendationPanel.add(buyPriceLabel);
        recommendationPanel.add(buyQuantityLabel);
        recommendationPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        recommendationPanel.add(sellPriceLabel);
        recommendationPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        recommendationPanel.add(expectedProfitLabel);
        recommendationPanel.add(statusLabel);
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(recommendationPanel, BorderLayout.CENTER);
    }
    
    private void doLogin() {
        String email = emailField.getText();
        String password = new String(passwordField.getPassword());
        
        if (email.isEmpty() || password.isEmpty()) {
            loginStatusLabel.setText("Please enter email and password");
            loginStatusLabel.setForeground(Color.RED);
            return;
        }
        
        loginButton.setEnabled(false);
        loginStatusLabel.setText("Logging in...");
        loginStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        
        client.configure(config.serverUrl(), null);
        client.login(email, password)
            .thenAccept(response -> {
                SwingUtilities.invokeLater(() -> {
                    client.setToken(response.getToken());
                    // Store token in config
                    // configManager.setConfiguration("arbitragepro", "jwtToken", response.getToken());
                    
                    isAuthenticated = true;
                    showMainPanel();
                    
                    log.info("Login successful, {} licenses found", 
                        response.getLicenses() != null ? response.getLicenses().length : 0);
                });
            })
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    loginButton.setEnabled(true);
                    loginStatusLabel.setText("Login failed: " + ex.getMessage());
                    loginStatusLabel.setForeground(Color.RED);
                });
                return null;
            });
    }
    
    private void checkExistingAuth() {
        client.configure(config.serverUrl(), config.jwtToken());
        client.verifyToken()
            .thenAccept(valid -> {
                if (valid) {
                    SwingUtilities.invokeLater(() -> {
                        isAuthenticated = true;
                        showMainPanel();
                    });
                }
            });
    }
    
    private void showMainPanel() {
        removeAll();
        add(mainPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        statusLabel.setText("Waiting for GE order...");
    }
    
    public void onPlayerLoggedIn() {
        if (isAuthenticated) {
            fetchRecommendation();
        }
    }
    
    public void onPlayerLoggedOut() {
        statusLabel.setText("Log into OSRS to get recommendations");
    }
    
    private void fetchRecommendation() {
        statusLabel.setText("Fetching recommendation...");
        
        // TODO: Get actual GP and GE slots from game client
        long availableGp = 10_000_000; // Placeholder
        int availableSlots = 4; // Placeholder
        String rsn = "PlayerName"; // TODO: Get from client
        
        client.getRecommendation(rsn, availableGp, availableSlots)
            .thenAccept(rec -> {
                SwingUtilities.invokeLater(() -> {
                    currentRecommendation = rec;
                    updateRecommendationDisplay(rec);
                });
            })
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + ex.getMessage());
                });
                return null;
            });
    }
    
    private void updateRecommendationDisplay(Recommendation rec) {
        itemNameLabel.setText(rec.getItem_name());
        buyPriceLabel.setText(String.format("BUY @ %,d gp", rec.getBuy_price()));
        buyQuantityLabel.setText(String.format("Quantity: %,d", rec.getBuy_quantity()));
        sellPriceLabel.setText(String.format("SELL @ %,d gp", rec.getSell_price()));
        expectedProfitLabel.setText(String.format("Expected Profit: %,d gp (%.1f%%)", 
            rec.getExpected_profit(), rec.getExpected_roi_percent()));
        statusLabel.setText("Place a GE buy order to start trade");
    }
    
    public Recommendation getCurrentRecommendation() {
        return currentRecommendation;
    }
    
    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }
}
```

---

### Task 4: GE Auto-Detection with Strict Validation
**Commit Point: "feat: auto-detect GE orders with strict validation"**

**tracking/TradeTracker.java:**
```java
package com.arbitragepro.tracking;

import com.arbitragepro.ArbitrageProPanel;
import com.arbitragepro.ArbitrageProOverlay;
import com.arbitragepro.api.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class TradeTracker {
    
    private final ArbitrageProClient client;
    private final ArbitrageProPanel panel;
    private final ArbitrageProOverlay overlay;
    
    // Track active trades by GE slot
    private final Map<Integer, TradeState> slotToTrade = new HashMap<>();
    
    // Track which slots we've already processed
    private final Map<Integer, GrandExchangeOfferState> lastKnownState = new HashMap<>();
    
    @Inject
    public TradeTracker(ArbitrageProClient client, ArbitrageProPanel panel, ArbitrageProOverlay overlay) {
        this.client = client;
        this.panel = panel;
        this.overlay = overlay;
    }
    
    public void handleOfferChanged(int slot, GrandExchangeOffer offer) {
        GrandExchangeOfferState state = offer.getState();
        GrandExchangeOfferState previousState = lastKnownState.get(slot);
        lastKnownState.put(slot, state);
        
        // Skip if state hasn't meaningfully changed
        if (state == previousState) {
            // But still check quantity updates
            updateQuantityIfNeeded(slot, offer);
            return;
        }
        
        log.debug("Slot {} state changed: {} -> {}", slot, previousState, state);
        
        switch (state) {
            case BUYING:
                handleNewBuyOrder(slot, offer);
                break;
            case BOUGHT:
                handleBuyComplete(slot, offer);
                break;
            case SELLING:
                handleNewSellOrder(slot, offer);
                break;
            case SOLD:
                handleSellComplete(slot, offer);
                break;
            case CANCELLED_BUY:
            case CANCELLED_SELL:
                handleCancelled(slot, offer);
                break;
            case EMPTY:
                handleSlotCleared(slot);
                break;
        }
    }
    
    private void handleNewBuyOrder(int slot, GrandExchangeOffer offer) {
        Recommendation rec = panel.getCurrentRecommendation();
        
        if (rec == null) {
            log.debug("Buy order placed but no active recommendation");
            return;
        }
        
        int itemId = offer.getItemId();
        int price = offer.getPrice();
        int quantity = offer.getTotalQuantity();
        
        // === STRICT VALIDATION ===
        
        // Check exact item ID match
        if (itemId != rec.getItem_id()) {
            log.info("Item mismatch: placed {} but recommendation is for {}", 
                itemId, rec.getItem_id());
            overlay.showWarning("Wrong item! Recommendation is for: " + rec.getItem_name());
            return;
        }
        
        // Check exact price match
        if (price != rec.getBuy_price()) {
            log.info("Price mismatch: placed {}gp but recommendation is {}gp", 
                price, rec.getBuy_price());
            overlay.showWarning(String.format(
                "Wrong price! Set exactly %,d gp (you entered %,d gp)",
                rec.getBuy_price(), price
            ));
            return;
        }
        
        // Quantity can be less than or equal to recommendation
        if (quantity > rec.getBuy_quantity()) {
            log.info("Quantity exceeds recommendation: {} > {}", 
                quantity, rec.getBuy_quantity());
            overlay.showWarning(String.format(
                "Quantity too high! Max recommended: %,d",
                rec.getBuy_quantity()
            ));
            return;
        }
        
        // === VALIDATION PASSED - Create trade ===
        log.info("Buy order matches recommendation! Creating trade...");
        panel.setStatus("Creating trade...");
        
        client.createTrade(itemId, price, quantity, rec.getRecommendation_id())
            .thenAccept(response -> {
                TradeState trade = new TradeState();
                trade.setTradeId(response.getTrade_id());
                trade.setItemId(itemId);
                trade.setBuyPrice(price);
                trade.setBuyQuantity(quantity);
                trade.setSellPrice(rec.getSell_price());
                trade.setStatus("buying");
                
                slotToTrade.put(slot, trade);
                
                panel.setStatus(String.format("Buying %,d / %,d...", 0, quantity));
                overlay.showSuccess("Trade created! Buying...");
            })
            .exceptionally(ex -> {
                log.error("Failed to create trade", ex);
                panel.setStatus("Error creating trade");
                overlay.showError("Failed to create trade: " + ex.getMessage());
                return null;
            });
    }
    
    private void handleBuyComplete(int slot, GrandExchangeOffer offer) {
        TradeState trade = slotToTrade.get(slot);
        
        if (trade == null) {
            log.debug("Buy complete but no tracked trade for slot {}", slot);
            return;
        }
        
        log.info("Buy complete for trade {}! Updating status to 'bought'", trade.getTradeId());
        
        trade.setStatus("bought");
        trade.setBuyQuantityFilled(offer.getQuantitySold());
        
        client.updateTrade(trade.getTradeId(), "bought", offer.getQuantitySold())
            .thenAccept(response -> {
                panel.setStatus(String.format(
                    "Buy complete! Now SELL @ %,d gp",
                    trade.getSellPrice()
                ));
                overlay.showSuccess(String.format(
                    "Now sell at exactly %,d gp!",
                    trade.getSellPrice()
                ));
            })
            .exceptionally(ex -> {
                log.error("Failed to update trade", ex);
                return null;
            });
    }
    
    private void handleNewSellOrder(int slot, GrandExchangeOffer offer) {
        TradeState trade = findTradeByItemId(offer.getItemId());
        
        if (trade == null || !"bought".equals(trade.getStatus())) {
            log.debug("Sell order placed but no matching bought trade");
            return;
        }
        
        int price = offer.getPrice();
        int quantity = offer.getTotalQuantity();
        
        // === STRICT VALIDATION ===
        
        // Check exact price match
        if (price != trade.getSellPrice()) {
            log.info("Sell price mismatch: placed {}gp but should be {}gp", 
                price, trade.getSellPrice());
            overlay.showWarning(String.format(
                "Wrong sell price! Set exactly %,d gp (you entered %,d gp)",
                trade.getSellPrice(), price
            ));
            return;
        }
        
        // Quantity check
        if (quantity > trade.getBuyQuantityFilled()) {
            log.info("Sell quantity exceeds held: {} > {}", 
                quantity, trade.getBuyQuantityFilled());
            overlay.showWarning(String.format(
                "You only have %,d to sell!",
                trade.getBuyQuantityFilled()
            ));
            return;
        }
        
        // === VALIDATION PASSED - Update trade ===
        log.info("Sell order matches! Updating trade to 'selling'");
        
        trade.setStatus("selling");
        trade.setSellQuantity(quantity);
        slotToTrade.put(slot, trade);
        
        client.updateTrade(trade.getTradeId(), "selling", null)
            .thenAccept(response -> {
                panel.setStatus(String.format("Selling %,d / %,d...", 0, quantity));
            })
            .exceptionally(ex -> {
                log.error("Failed to update trade", ex);
                return null;
            });
    }
    
    private void handleSellComplete(int slot, GrandExchangeOffer offer) {
        TradeState trade = slotToTrade.get(slot);
        
        if (trade == null) {
            log.debug("Sell complete but no tracked trade for slot {}", slot);
            return;
        }
        
        log.info("Sell complete for trade {}!", trade.getTradeId());
        
        int quantitySold = offer.getQuantitySold();
        int profit = (trade.getSellPrice() * quantitySold) - 
                     (trade.getBuyPrice() * trade.getBuyQuantityFilled());
        
        trade.setStatus("completed");
        
        client.updateTrade(trade.getTradeId(), "completed", quantitySold)
            .thenAccept(response -> {
                slotToTrade.remove(slot);
                
                panel.setStatus("Trade complete! Fetching next recommendation...");
                overlay.showSuccess(String.format(
                    "Trade complete! Profit: %,d gp",
                    profit
                ));
                
                // Fetch next recommendation
                // panel.fetchRecommendation();
            })
            .exceptionally(ex -> {
                log.error("Failed to complete trade", ex);
                return null;
            });
    }
    
    private void handleCancelled(int slot, GrandExchangeOffer offer) {
        TradeState trade = slotToTrade.get(slot);
        
        if (trade != null) {
            log.info("Trade {} cancelled", trade.getTradeId());
            
            client.updateTrade(trade.getTradeId(), "cancelled", null)
                .thenAccept(response -> {
                    slotToTrade.remove(slot);
                    panel.setStatus("Trade cancelled. Ready for new recommendation.");
                });
        }
    }
    
    private void handleSlotCleared(int slot) {
        slotToTrade.remove(slot);
        lastKnownState.remove(slot);
    }
    
    private void updateQuantityIfNeeded(int slot, GrandExchangeOffer offer) {
        TradeState trade = slotToTrade.get(slot);
        
        if (trade == null) {
            return;
        }
        
        int filled = offer.getQuantitySold();
        int total = offer.getTotalQuantity();
        
        if ("buying".equals(trade.getStatus())) {
            panel.setStatus(String.format("Buying %,d / %,d...", filled, total));
        } else if ("selling".equals(trade.getStatus())) {
            panel.setStatus(String.format("Selling %,d / %,d...", filled, total));
        }
        
        // Optionally report progress to backend
        // client.updateTrade(trade.getTradeId(), null, filled);
    }
    
    private TradeState findTradeByItemId(int itemId) {
        return slotToTrade.values().stream()
            .filter(t -> t.getItemId() == itemId)
            .findFirst()
            .orElse(null);
    }
}
```

**tracking/TradeState.java:**
```java
package com.arbitragepro.tracking;

import lombok.Data;

@Data
public class TradeState {
    private int tradeId;
    private int itemId;
    private int buyPrice;
    private int buyQuantity;
    private int buyQuantityFilled;
    private int sellPrice;
    private int sellQuantity;
    private String status; // buying, bought, selling, completed, cancelled
}
```

---

### Task 5: Notifications & Overlays
**Commit Point: "feat: add overlay notifications"**

**ArbitrageProOverlay.java:**
```java
package com.arbitragepro;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@Slf4j
public class ArbitrageProOverlay extends Overlay {
    
    private final Client client;
    private final ArbitrageProConfig config;
    private final PanelComponent panelComponent = new PanelComponent();
    
    // Notification state
    private String currentMessage = null;
    private Color currentColor = Color.WHITE;
    private Instant messageExpiry = null;
    
    private static final int MESSAGE_DURATION_MS = 5000;
    
    @Inject
    public ArbitrageProOverlay(Client client, ArbitrageProConfig config) {
        this.client = client;
        this.config = config;
        
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlays()) {
            return null;
        }
        
        if (currentMessage == null || messageExpiry == null) {
            return null;
        }
        
        if (Instant.now().isAfter(messageExpiry)) {
            currentMessage = null;
            return null;
        }
        
        panelComponent.getChildren().clear();
        
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Arbitrage Pro")
            .color(currentColor)
            .build());
        
        panelComponent.getChildren().add(LineComponent.builder()
            .left(currentMessage)
            .build());
        
        panelComponent.setPreferredSize(new Dimension(250, 0));
        
        return panelComponent.render(graphics);
    }
    
    public void showWarning(String message) {
        show(message, Color.ORANGE);
    }
    
    public void showError(String message) {
        show(message, Color.RED);
    }
    
    public void showSuccess(String message) {
        show(message, Color.GREEN);
    }
    
    public void showInfo(String message) {
        show(message, Color.WHITE);
    }
    
    private void show(String message, Color color) {
        log.info("[Overlay] {}", message);
        this.currentMessage = message;
        this.currentColor = color;
        this.messageExpiry = Instant.now().plusMillis(MESSAGE_DURATION_MS);
    }
    
    public void clear() {
        this.currentMessage = null;
        this.messageExpiry = null;
    }
}
```

---

### Task 6: API DTOs & Completion
**Commit Point: "feat: complete API models"**

**api/Recommendation.java:**
```java
package com.arbitragepro.api;

import lombok.Data;

@Data
public class Recommendation {
    private int recommendation_id;
    private int item_id;
    private String item_name;
    private int buy_price;
    private int buy_quantity;
    private int sell_price;
    private int expected_profit;
    private double expected_roi_percent;
}
```

**api/ActiveTrade.java:**
```java
package com.arbitragepro.api;

import lombok.Data;

@Data
public class ActiveTrade {
    private int trade_id;
    private int item_id;
    private String item_name;
    private int buy_price;
    private int buy_quantity;
    private int buy_quantity_filled;
    private int sell_price;
    private int sell_quantity_filled;
    private String status;
    private boolean is_stale;
}
```

**api/TradeCreateResponse.java:**
```java
package com.arbitragepro.api;

import lombok.Data;

@Data
public class TradeCreateResponse {
    private int trade_id;
    private String status;
}
```

**api/TradeUpdateResponse.java:**
```java
package com.arbitragepro.api;

import lombok.Data;

@Data
public class TradeUpdateResponse {
    private boolean success;
    private ActiveTrade trade;
    private String action; // wait, sell, complete
    private Integer sell_price;
}
```

---

## API Contract Summary

The plugin communicates with these backend endpoints (running separately):

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/auth/login` | POST | Authenticate user, get JWT token |
| `/auth/verify` | GET | Verify JWT token validity |
| `/recommendations` | GET | Get personalized recommendation based on GP/slots |
| `/trades/create` | POST | Create new active trade when buy order matches |
| `/trades/:id/update` | POST | Update trade progress/status |
| `/trades/active` | GET | Get all active trades for user |

---

## Backend Server Configuration

The plugin expects the backend server (developed separately) to be running with these characteristics:

### Default Configuration
- **Server URL:** `https://api.arbitragepro.gg` (configurable in plugin settings)
- **Authentication:** JWT token in `Authorization: Bearer <token>` header
- **Response Format:** JSON
- **CORS:** Must allow requests from RuneLite client

### Request/Response Examples

**Login Request:**
```json
POST /auth/login
{
  "email": "user@example.com",
  "password": "userpassword"
}
```

**Login Response:**
```json
{
  "user_id": 123,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "licenses": [
    {
      "license_id": 456,
      "runescape_username": "PlayerName",
      "status": "active",
      "subscription_end": "2026-02-15T00:00:00Z"
    }
  ]
}
```

**Get Recommendation Request:**
```http
GET /recommendations?runescape_username=PlayerName&available_gp=10000000&available_ge_slots=4
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Recommendation Response:**
```json
{
  "recommendation_id": 789,
  "item_id": 2353,
  "item_name": "Steel bar",
  "buy_price": 450,
  "buy_quantity": 1000,
  "sell_price": 520,
  "expected_profit": 70000,
  "expected_roi_percent": 15.56
}
```

---

## Testing Checklist

Before submitting to RuneLite plugin hub, verify:

### Core Functionality
- [ ] Plugin loads in RuneLite without errors
- [ ] Configuration panel shows all settings correctly
- [ ] Login flow works with backend server
- [ ] JWT token is stored and persists between sessions
- [ ] Recommendation fetches and displays correctly in panel

### GE Order Detection
- [ ] Buy order detection works for matching recommendations
- [ ] **Strict validation rejects wrong item ID**
- [ ] **Strict validation rejects wrong price (even ±1gp difference)**
- [ ] **Quantity validation allows less than or equal to recommendation**
- [ ] Matching buy order creates trade on backend server
- [ ] Buy completion triggers "now sell" status and overlay prompt

### Sell Order Detection  
- [ ] Sell order detection works for active trades
- [ ] **Strict validation rejects wrong sell price**
- [ ] **Quantity validation prevents overselling**
- [ ] Sell completion logs profit and clears trade

### Error Handling
- [ ] Network errors are handled gracefully
- [ ] Invalid credentials show appropriate error messages
- [ ] Server downtime doesn't crash plugin
- [ ] Overlay notifications appear and auto-dismiss after 5 seconds
- [ ] Cancelled orders are handled and cleaned up properly

### UI/UX
- [ ] Panel switches correctly between login and main views
- [ ] Status messages update appropriately during trade lifecycle
- [ ] Overlay positioning doesn't interfere with game UI
- [ ] Configuration settings persist and take effect

---

## Development Environment Setup

### Prerequisites
- Java 11 or higher
- Maven 3.6+
- RuneLite client (for testing)
- Access to Arbitrage Pro backend server

### Build & Test Process
1. Clone the separate plugin repository
2. Set up backend server locally or use staging environment
3. Build plugin: `mvn clean package`
4. Load plugin in RuneLite via developer mode
5. Test authentication flow
6. Test with live GE orders in-game
7. Verify all validation scenarios

### Debugging
- Use `@Slf4j` logging throughout
- RuneLite console shows all log output
- Test both valid and invalid order scenarios
- Monitor backend server logs for API calls

---

## RuneLite Plugin Hub Submission

### Requirements for Submission
- ✅ Plugin must be open source (this repository)
- ✅ Must follow RuneLite plugin guidelines
- ✅ No malicious code or data harvesting
- ✅ Proper error handling and stability
- ✅ Clear description and documentation

### Files to Include
- Complete source code in this repository
- Clear README.md explaining functionality
- LICENSE file (typically GPL-3.0 for RuneLite plugins)
- Plugin icon and assets
- Version in pom.xml

### Note on Private Components
The backend server, ML models, and business logic remain private and separate. Only this plugin client code needs to be open source for RuneLite submission.

---

## Coordination Notes

This plugin repository works independently but coordinates with:

1. **Backend Server** (separate private repo): Provides API endpoints, handles authentication, generates recommendations
2. **ML Services** (separate private repo): Generates the recommendations that backend serves
3. **Data Collection** (separate private repo): Feeds price data to ML pipeline

The plugin is just the client-side UI and API consumer - all intelligence is server-side.