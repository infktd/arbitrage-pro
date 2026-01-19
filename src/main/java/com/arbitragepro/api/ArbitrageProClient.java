package com.arbitragepro.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ArbitrageProClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final OkHttpClient client;
    private final Gson gson;
    private String authToken;

    public ArbitrageProClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    public String getAuthToken() {
        return authToken;
    }

    public boolean isAuthenticated() {
        return authToken != null && !authToken.isEmpty();
    }

    /**
     * Register a new user account
     */
    public AuthResponse register(String email, String password) throws ApiException {
        JsonObject json = new JsonObject();
        json.addProperty("email", email);
        json.addProperty("password", password);

        try {
            String responseBody = post("/auth/register", json.toString(), false);
            AuthResponse response = gson.fromJson(responseBody, AuthResponse.class);
            this.authToken = response.getToken();
            return response;
        } catch (IOException e) {
            throw new ApiException("Network error during registration: " + e.getMessage(), -1);
        }
    }

    /**
     * Login with existing credentials
     */
    public AuthResponse login(String email, String password) throws ApiException {
        JsonObject json = new JsonObject();
        json.addProperty("email", email);
        json.addProperty("password", password);

        try {
            String responseBody = post("/auth/login", json.toString(), false);
            AuthResponse response = gson.fromJson(responseBody, AuthResponse.class);
            this.authToken = response.getToken();
            return response;
        } catch (IOException e) {
            throw new ApiException("Network error during login: " + e.getMessage(), -1);
        }
    }

    /**
     * Get a recommendation for the user
     */
    public Recommendation getRecommendation(String runescapeUsername, int availableGp, int availableSlots) throws ApiException {
        String url = String.format("/recommendations?runescape_username=%s&available_gp=%d&available_ge_slots=%d",
                runescapeUsername, availableGp, availableSlots);

        try {
            String responseBody = get(url);
            return gson.fromJson(responseBody, Recommendation.class);
        } catch (IOException e) {
            throw new ApiException("Network error fetching recommendation: " + e.getMessage(), -1);
        }
    }

    /**
     * Create a new trade
     */
    public TradeCreateResponse createTrade(int itemId, int buyPrice, int buyQuantity) throws ApiException {
        JsonObject json = new JsonObject();
        json.addProperty("item_id", itemId);
        json.addProperty("buy_price", buyPrice);
        json.addProperty("buy_quantity", buyQuantity);

        try {
            String responseBody = post("/trades/create", json.toString(), true);
            return gson.fromJson(responseBody, TradeCreateResponse.class);
        } catch (IOException e) {
            throw new ApiException("Network error creating trade: " + e.getMessage(), -1);
        }
    }

    /**
     * Update trade status (bought, selling, completed)
     */
    public TradeUpdateResponse updateTrade(int tradeId, String status, int quantityFilled) throws ApiException {
        JsonObject json = new JsonObject();
        json.addProperty("status", status);
        json.addProperty("quantity_filled", quantityFilled);

        try {
            String responseBody = post("/trades/" + tradeId + "/update", json.toString(), true);
            return gson.fromJson(responseBody, TradeUpdateResponse.class);
        } catch (IOException e) {
            throw new ApiException("Network error updating trade: " + e.getMessage(), -1);
        }
    }

    /**
     * Get active trade if one exists
     */
    public ActiveTrade getActiveTrade() throws ApiException {
        try {
            String responseBody = get("/trades/active");
            ActiveTrade[] trades = gson.fromJson(responseBody, ActiveTrade[].class);
            return (trades != null && trades.length > 0) ? trades[0] : null;
        } catch (IOException e) {
            throw new ApiException("Network error fetching active trade: " + e.getMessage(), -1);
        }
    }

    // ================== HTTP HELPERS ==================

    private String get(String endpoint) throws IOException, ApiException {
        Request request = new Request.Builder()
                .url(baseUrl + endpoint)
                .header("Authorization", "Bearer " + authToken)
                .get()
                .build();

        return executeRequest(request);
    }

    private String post(String endpoint, String jsonBody, boolean requiresAuth) throws IOException, ApiException {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + endpoint)
                .post(body);

        if (requiresAuth && authToken != null) {
            builder.header("Authorization", "Bearer " + authToken);
        }

        return executeRequest(builder.build());
    }

    private String executeRequest(Request request) throws IOException, ApiException {
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                // Try to parse error message from JSON
                String errorMessage = responseBody;
                try {
                    JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                    if (errorJson.has("error")) {
                        errorMessage = errorJson.get("error").getAsString();
                    }
                } catch (Exception e) {
                    // Keep original responseBody as error message
                }

                throw new ApiException(errorMessage, response.code());
            }

            return responseBody;
        }
    }
}
