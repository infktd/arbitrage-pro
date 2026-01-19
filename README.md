# Arbitrage Pro - RuneLite Plugin

GE arbitrage recommendations powered by machine learning predictions.

## Features

- **ML-Powered Recommendations**: Get arbitrage opportunities ranked by a 93% accurate ensemble model
- **Real-Time Price Validation**: Checks latest prices before tracking trades to prevent losses from stale recommendations
- **Auto-Tracking**: Automatically detects when you place GE orders matching recommendations
- **Zero Extra Clicks**: Just trade normally, plugin handles everything
- **Real-Time Updates**: Syncs trade status with backend automatically
- **Smart Fairness**: Per-user recommendation cooldowns prevent duplicate suggestions
- **Liquidity Warnings**: Alerts you when items have low trading volume

## Installation

### For Developers

```bash
# Build plugin
./gradlew build

# Run in development mode
./gradlew run
```

## Configuration

1. Open RuneLite settings
2. Navigate to Arbitrage Pro
3. Configure:
   - **API URL**: Backend API endpoint (default: `http://localhost:8000`)
   - **Email**: Your Arbitrage Pro account email
   - **Password**: Your account password
   - **RS Username**: Your RuneScape character name
   - **Auto Login**: Automatically login when plugin starts (default: enabled)
   - **Auto Track Orders**: Automatically detect and track GE orders (default: enabled)
   - **Show Notifications**: Show in-game notifications for trade updates (default: enabled)
   - **Validate Prices**: Check real-time prices before tracking trades (default: enabled)

## Usage

1. **Login**: Click "Login" in the Arbitrage Pro sidebar panel
2. **Get Recommendation**: Click "Get Recommendation" to fetch opportunity
3. **Place Buy Order**: Go to GE and place buy order at EXACT price shown
4. **Auto-Tracking**: Plugin automatically detects and tracks your trade
5. **Price Validation**: Plugin validates real-time prices when you place orders
   - ⚠️ **Price moved**: Warning if buy/sell prices changed >2%
   - ⚠️ **Low liquidity**: Warning if no trades in last 10 minutes
   - 🛡️ **Protection**: Option to cancel if recommendation is stale
6. **Sell**: Plugin tells you when to sell and at what price

### Price Validation Details

When you place a GE buy order, the plugin checks OSRS Wiki's real-time prices:
- **Prevents losses** from stale recommendations (bot dumps, price spikes)
- **Warns about low liquidity** items that may take hours to fill
- **Graceful fallback** if validation API is unavailable (never blocks you)
- **30-second cache** per item to respect API rate limits

## Project Structure

```
arbitrage-pro-plugin/
├── src/main/java/com/arbitragepro/
│   ├── ArbitrageProPlugin.java     # Main plugin
│   ├── ArbitrageProConfig.java     # Configuration
│   ├── ArbitrageProPanel.java      # UI panel
│   └── api/                        # API client & DTOs
└── src/test/java/com/arbitragepro/
    └── ArbitrageProTest.java       # Test launcher
```

## Building

```bash
# Compile
./gradlew build

# Run in development mode
./gradlew run

# Create JAR
./gradlew shadowJar
```

## Testing

Ensure backend is running:
```bash
curl http://localhost:8000/health
```

Then launch plugin:
```bash
./gradlew run
```

## Documentation

See `.claude/` directory for detailed documentation:
- `PLUGIN_API_DOCUMENTATION.md` - Complete API reference
- `PLUGIN_HANDOFF_SUMMARY.md` - Project overview
- `RUNELITE_PLUGIN_SEPARATE_REPO_INSTRUCTIONS.md` - Implementation guide
