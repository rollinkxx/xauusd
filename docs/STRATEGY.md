# Strategy specification

## Status and intent

`trend-pullback-v1.0` is an **unvalidated rule-based research candidate**. It is not an investment recommendation. No performance, accuracy, probability-of-win, or robustness claim is made. The strategy emits `WAIT` when data is insufficient, higher-timeframe filters disagree, or volatility exceeds its rule threshold.

## Inputs and timestamp rules

- Provider series: XAU/USD, 5-minute OHLC, requested with `timezone=UTC`.
- Invalid OHLC records are dropped; duplicate timestamps are deduplicated; candles are sorted chronologically.
- 15-minute, 1-hour and 4-hour bars are aggregated from 5-minute candles. These are derived bar views, not independent provider quotes.
- EMA/RSI/ATR use only observations at or before the signal evaluation point.

## Candidate entry rules

BUY requires all of: 5m close above EMA(20), 15m close above EMA(20), 1h close above EMA(20), 4h close above EMA(20), and 15m RSI(14) from 50 through 70. SELL is the inverse trend alignment with RSI from 30 through 50. Otherwise WAIT. At least 260 valid 5m bars and 20 bars in each resampled timeframe are required.

ATR(14) on 5m is the volatility measure. ATR/close above 0.4% is labeled high volatility and blocks entry. Candidate stop distance is 1.5 × ATR; target distance is 2 × stop distance. Minimum risk/reward is configurable (default 1.5). Signal score is a rule score (70 base, up to 20 additional points); it is not calibrated and should not be interpreted as probability.

## Simulation and risk

- Starting virtual balance: USD 10,000.
- Risk per trade defaults to 1%, adjustable from 0.25% to 2%.
- Quantity is calculated as equity × risk fraction ÷ stop distance, and shown in ounces. Broker lot/contract specifications are not asserted.
- Entry and exit use an adjustable USD/oz spread assumption (default 0.30) and adverse slippage assumption (default 0.05). These are simulation assumptions, not provider/broker quotes.
- One open position maximum, a 2% realized daily-loss guard and 30-minute cooldown after a loss.
- Duplicate signal fingerprint includes strategy version, candle timestamp and direction; last handled fingerprint survives process restarts.
- When a candle touches both stop and target, stop wins. Backtest market entry is next-bar open; a stop gap is filled at the worse open/stop. Costs are deducted through adverse entry/exit fills.
- No commission or margin is modeled. Historical drawdown and expectancy remain unavailable until a real history is supplied and verified.

## Validation status

No parameter calibration or selection was performed. Candidate comparison is hypothesis-only, not performance testing. No training/validation/out-of-sample split, walk-forward, parameter stability stress test, or regime robustness results are claimed. See [VALIDATION.md](VALIDATION.md).
