package com.arbitragepro;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("arbitragepro")
public interface ArbitrageProConfig extends Config {

    @ConfigItem(
            keyName = "apiUrl",
            name = "API URL",
            description = "Backend API URL (default: http://localhost:8000)"
    )
    default String apiUrl() {
        return "http://localhost:8000";
    }

    @ConfigItem(
            keyName = "email",
            name = "Email",
            description = "Your Arbitrage Pro account email"
    )
    default String email() {
        return "";
    }

    @ConfigItem(
            keyName = "password",
            name = "Password",
            description = "Your Arbitrage Pro account password",
            secret = true
    )
    default String password() {
        return "";
    }

    @ConfigItem(
            keyName = "runescapeUsername",
            name = "RS Username",
            description = "Your RuneScape character name"
    )
    default String runescapeUsername() {
        return "";
    }

    @ConfigItem(
            keyName = "autoLogin",
            name = "Auto Login",
            description = "Automatically login when plugin starts"
    )
    default boolean autoLogin() {
        return true;
    }

    @ConfigItem(
            keyName = "autoTrack",
            name = "Auto Track Orders",
            description = "Automatically detect and track GE orders"
    )
    default boolean autoTrack() {
        return true;
    }

    @ConfigItem(
            keyName = "showNotifications",
            name = "Show Notifications",
            description = "Show in-game notifications for trade updates"
    )
    default boolean showNotifications() {
        return true;
    }

    @ConfigItem(
            keyName = "validatePrices",
            name = "Validate Prices",
            description = "Check real-time prices before tracking trades (prevents stale recommendations)"
    )
    default boolean validatePrices() {
        return true;
    }
}
