# Validation and research status

## Test coverage

- Twelve Data parser protocol: JSON errors, provider symbol, UTC conversion, duplicate records, malformed rows, and invalid OHLC.
- Gold API parser: accepts only XAU/USD, validates finite positive prices and timestamps, and intentionally returns no candle history.
- Market math: candle validation/de-duplication/sorting, resampling, EMA, RSI, ATR, and insufficient windows.
- Strategy and backtest: WAIT on insufficient history and safe empty-window metrics.
- Paper trading: risk-based sizing, spread/slippage fills, costs, and conservative same-candle SL/TP priority.
- Android instrumentation smoke test covers launch without login, dashboard presence, settings navigation, and no-login behavior; the corrected test still requires an emulator run to confirm.

Protocol unit fixtures are synthetic and are never displayed or used as production market data. No real market history is bundled.

## Live data evidence and limits

On 2026-09-29, `https://api.gold-api.com/price/XAU/USD` returned HTTP 200 from the sandbox with `symbol=XAU`, `currency=USD`, a positive `price`, and `updatedAt`. This verifies reachability and response shape only; it does **not** independently establish price accuracy or a live-market SLA. The keyless endpoint supplies a current quote but no candle history, so the app must show `WAIT`, no chart candles, and no new paper entries when this provider is selected.

Twelve Data's public `demo` key returned HTTP 401 in earlier probes. No entitled Twelve Data user key was available; its XAU/USD 5-minute candles, plan entitlement, latency, and licensing therefore remain unverified until a qualifying user key is entered and tested. A key from API Ninjas is not interchangeable and its Gold Price endpoint is gold futures, not XAU/USD.

## Strategy validation

- Dataset period: none supplied or downloaded in this implementation session.
- Training/validation/out-of-sample dates: not available.
- Walk-forward windows and cost/slippage stress tests: not run.
- Candidate parameter selection: no empirical optimization performed.
- Anti-leakage review: backtest enters at next-bar open and resolves same-bar SL/TP ambiguity conservatively to SL, but a full dataset-level timestamp/missing-bar/walk-forward audit remains unverified.

Core Android unit tests, build, and lint must be measured in the current CI run before any pass claim. No out-of-sample, walk-forward, profitability, or "research validated" claim is made. When a licensed dataset is available, document its source/period, preserve chronological train/validation/test splits, avoid test-period tuning, run walk-forward and cost sensitivity, and report sample size and drawdown alongside expectancy/profit factor.
