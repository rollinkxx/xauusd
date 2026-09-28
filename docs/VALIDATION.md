# Validation and research status

## Test coverage in the repository

- Parser protocol behavior: JSON errors, provider symbol, timestamp conversion, duplicate records, malformed rows and bad OHLC.
- Market math: candle validation/de-duplication/sorting, resampling, EMA, RSI, ATR and insufficient windows.
- Strategy: WAIT when history is insufficient.
- Paper trading: risk-based size, spread/slippage fills, costs and same-candle SL/TP conservative priority.
- Backtest: safe empty/insufficient-history metrics.
- Android instrumentation test covers launch without login, dashboard presence and Settings navigation. The last attempted run found a case-sensitive mismatch in its expected Settings heading; the assertion was corrected to match the UI's all-caps rendering. A PR or manual-dispatch run is still needed to confirm the corrected test passes.

Protocol unit fixtures are labeled synthetic parser fixtures and are never displayed or loaded into production. No real market history is bundled. Real provider integration is separate from unit tests.

## Dataset and evaluation

- Dataset period: none supplied or downloaded in this implementation session.
- Training/validation/out-of-sample dates: not available.
- Walk-forward windows: not run.
- Candidate parameter selection attempts: none documented because no empirical optimization was run.
- Cost and slippage stress scenarios: assumptions exist in the simulator; historical stress evaluation is not run.
- Anti-leakage review: signal calculation uses only the preceding candles and backtest entry is next-bar open; same-candle ambiguity gives priority to SL. A complete dataset-level look-ahead, timestamp, missing-bar and walk-forward audit remains unverified.

## Honest statuses

- Core deterministic unit tests: measured in CI.
- Android unit tests, build and lint: passed in CI. Corrected emulator instrumentation: pending a PR/manual CI run.
- Real feed / data freshness: blocked until a user supplies an entitled key; public demo key probes returned HTTP 401.
- Backtest on licensed real data: not run; do not cite performance metrics.
- Out-of-sample, walk-forward and regime robustness: unverified.
- “Research validated” status: **not achieved**.

When a licensed dataset is available, save the dataset period and provider/source, preserve a chronological train/validation/test split, avoid test-period tuning, run rolling walk-forward and cost sensitivity checks, and report sample size and drawdown alongside expectancy/profit factor. Keep live-paper metrics distinct from historical tests.
