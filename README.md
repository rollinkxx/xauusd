# XAUUSD Signal Lab

A lightweight native Android **paper-trading** monitor for XAU/USD. It never connects to a broker and never sends a real order. The dashboard starts without a login. Until a valid, entitled Twelve Data API key is entered, it displays an offline/configuration state and creates no market price, candle, signal, or trade.

## Current behavior

- Kotlin + Jetpack Compose; Android SQLite for local positions and history.
- Provider adapter contract with a Twelve Data adapter requesting the documented `XAU/USD` 5-minute time series over HTTPS.
- API key is supplied by the user, encrypted with Android Keystore AES-GCM and not checked into the repository or logged.
- Market data is refreshed only while the app is foregrounded, every five minutes; network failures use bounded exponential retry and never fall back to fabricated values.
- Validated 5-minute candles are aggregated locally into 15-minute, 1-hour and 4-hour chart and strategy inputs. Bid/ask are not supplied by this endpoint and are shown as unavailable.
- Candidate `trend-pullback-v1.0` strategy uses 4h/1h EMA trend, 15m RSI, 5m EMA confirmation, ATR-based stop and a nominal 2R target. It is **not empirically validated** and its score is not a win probability.
- Auto paper trading is enabled as a setting but remains inert until fresh provider data and a qualifying signal exist. Position sizing is expressed in ounces; no broker contract size or margin is invented. Configurable spread/slippage assumptions are recorded per position.
- Daily realized-loss guard (2% of equity), one-position maximum and 30-minute loss cooldown. Same-candle SL and TP ambiguity resolves to SL.
- Native Canvas chart, data status, positions, trade history, summary statistics and settings.

## Provider and license prerequisites

Twelve Data lists Gold Spot US Dollar (`XAU/USD`) in its commodity catalog and documents historical `time_series` intervals including 5 minutes. Its current individual pricing page groups commodity market access with a higher plan, so a basic/free key may not be entitled. The app does not include a global API key. Supply a key and plan that explicitly entitles you to XAU/USD and intraday time series. The provider's demo key did not return price data in the implementation environment; live-feed verification is therefore **BLOCKED** until a qualifying user key is configured.

The provider terms limit data use to the subscription's allowed scope, and external display/redistribution can require explicit rights. This app is for the key owner's personal, non-commercial use; do not redistribute market data. Confirm the current plan, retention and display license with Twelve Data before commercial or public redistribution. The UI includes a `Source: Twelve Data` attribution.

## Build locally

Requirements: JDK 17+, Android SDK platform 36, and Android Build Tools. Gradle Wrapper is pinned to 8.13; Android Gradle Plugin 8.13.0 and Kotlin 2.3.0 are pinned in the root build file.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release output is unsigned unless a release signing configuration is added separately; no keystore belongs in Git.

## GitHub Actions

`.github/workflows/android.yml` runs for pushes to `main`, pull requests, and manual dispatch. Every run validates the wrapper, runs unit tests and lint, assembles debug/release, and uploads `xauusd-android-apk` plus reports. Push runs skip emulator provisioning so the APK is uploaded as soon as build checks finish; the API 35 emulator smoke test runs on pull requests and manual dispatch, with a 15-minute step limit. The debug APK is signed with Gradle's debug key and intended for sideload validation, not Play Store publishing. The artifact contains SHA-256 checksums and build metadata. Open the repository's **Actions → Android CI → Artifacts** to download it.

## Safety and known limits

- No provider key was available during implementation, so no real XAU/USD response or real market behavior was verified.
- Provider requests use one 5-minute time-series call per refresh; actual freshness, access tier and quota depend on the user's Twelve Data account. HTTP success alone is not a latency/SLA guarantee.
- Candles have one provider timestamp and no bid/ask. The shown feed is a five-minute data series, not a universal OTC price or guaranteed tick stream.
- The strategy parameters are research defaults/hypotheses, not selected by training/validation/test or walk-forward results. No accuracy/profit claim is made.
- A chronological backtest engine exists, but it has not been run on a licensed real dataset in this environment. No out-of-sample, walk-forward, stress-test or anti-leakage result is claimed.
- No alerts/news calendar, equity curve/drawdown chart, or continuously running background service is included. Monitoring intentionally stops when the app leaves the foreground.
- Paper balance and trades are local to this device. Reset permanently clears them after confirmation.
