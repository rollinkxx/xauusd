# XAUUSD Signal Lab

A lightweight native Android **paper-trading** monitor for XAU/USD. It never connects to a broker and never sends a real order. The dashboard starts without a login. A fresh install defaults to Gold API for a current XAU/USD quote without a key (quote-only; no candle-based signals or new paper entries). Choose Twelve Data for historical candles and candidate signals when you have a key entitled to XAU/USD and intraday history.

## Current behavior

- Kotlin + Jetpack Compose; Android SQLite for local positions and history.
- Provider selector contains only sources that return XAU/USD: Twelve Data 5-minute candles and Gold API's current USD XAU quote.
- Twelve Data key is supplied by the user, encrypted with Android Keystore AES-GCM and stored separately by provider; Gold API's current-price endpoint needs no key.
- Market data is refreshed only while the app is foregrounded, every five minutes; network failures use bounded exponential retry and never fall back to fabricated values.
- Validated 5-minute candles are aggregated locally into 15-minute, 1-hour and 4-hour chart and strategy inputs. Bid/ask are not supplied by this endpoint and are shown as unavailable.
- Candidate `trend-pullback-v1.0` strategy uses 4h/1h EMA trend, 15m RSI, 5m EMA confirmation, ATR-based stop and a nominal 2R target. It is **not empirically validated** and its score is not a win probability.
- Auto paper trading is enabled as a setting but remains inert until fresh provider data and a qualifying signal exist. Position sizing is expressed in ounces; no broker contract size or margin is invented. Configurable spread/slippage assumptions are recorded per position.
- Daily realized-loss guard (2% of equity), one-position maximum and 30-minute loss cooldown. Same-candle SL and TP ambiguity resolves to SL.
- Native Canvas chart, data status, positions, trade history, summary statistics and settings.

## Provider and license prerequisites

Twelve Data lists Gold Spot US Dollar (`XAU/USD`) and documents a 5-minute `time_series`. A plan with commodity and intraday access is required; a basic/free key may not be entitled. The app does not bundle a provider key. Gold API provides a public no-key current quote at [`/price/XAU/USD`](https://api.gold-api.com/price/XAU/USD), but its historical and OHLC endpoints require a Gold API key; the no-key app mode therefore does not claim candle history, calculate signals, or open paper trades. The rendered Gold API webpage calls the same JSON endpoint, so this app uses that documented endpoint directly instead of scraping the page.

Twelve Data plan entitlements and redistribution/display rights depend on the subscription. Gold API's terms disclaim data accuracy and uninterrupted service; cache its public quote for at least 30 seconds and do not spam it. This app refreshes no more often than every five minutes and shows source attribution. Neither feed is a guaranteed universal OTC price. API Ninjas Gold Futures and Indodax XAUT/PAXG IDR markets are intentionally excluded: they are not XAU/USD spot, and the API Ninjas key entered in the old Twelve Data-only screen cannot authenticate with Twelve Data.

## Build locally

Requirements: JDK 17+, Android SDK platform 36, and Android Build Tools. Gradle Wrapper is pinned to 8.13; Android Gradle Plugin 8.13.0 and Kotlin 2.3.0 are pinned in the root build file.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release output is unsigned unless a release signing configuration is added separately; no keystore belongs in Git.

## GitHub Actions

`.github/workflows/android.yml` runs for pushes to `main`, pull requests, and manual dispatch. Every run validates the wrapper, runs unit tests and lint, assembles debug/release, and uploads `xauusd-android-apk` plus reports. Push runs skip emulator provisioning so the APK is uploaded as soon as build checks finish; the API 35 emulator smoke test runs on pull requests and manual dispatch, with a 15-minute step limit. The debug APK is signed with Gradle's debug key and intended for sideload validation, not Play Store publishing. The artifact contains SHA-256 checksums and build metadata. Open the repository's **Actions → Android CI → Artifacts** to download it.

## Safety and known limits

- The public Gold API current XAU/USD endpoint returned HTTP 200 in a sandbox probe; this verifies the endpoint response only, not independent price accuracy. Twelve Data historical candle access still requires the user's entitled key and has not been live-verified.
- Twelve Data requests one 5-minute time-series call per refresh. Gold API requests only a current XAU/USD quote and has no no-key history; actual freshness is source-dependent. HTTP success alone is not a latency/SLA guarantee.
- Candles have one provider timestamp and no bid/ask. The shown feed is a five-minute data series, not a universal OTC price or guaranteed tick stream.
- The strategy parameters are research defaults/hypotheses, not selected by training/validation/test or walk-forward results. No accuracy/profit claim is made.
- A chronological backtest engine exists, but it has not been run on a licensed real dataset in this environment. No out-of-sample, walk-forward, stress-test or anti-leakage result is claimed.
- No alerts/news calendar, equity curve/drawdown chart, or continuously running background service is included. Monitoring intentionally stops when the app leaves the foreground.
- Paper balance and trades are local to this device. Reset permanently clears them after confirmation.
