# Candidate strategy research notes

**Status: hypothesis inventory only.** No real XAU/USD historical dataset or provider key was available, so no candidate was backtested, scored, or selected as empirically best.

| Candidate | Hypothesis | Main failure mode to test | Implemented |
|---|---|---|---|
| Trend continuation | Align higher-timeframe trend and lower-timeframe momentum | Whipsaw/range losses; delayed entries | Prototype `trend-pullback-v1.0` |
| Breakout | Enter after range boundary break with volatility expansion | False breaks and spread/slippage sensitivity | No |
| Pullback | Enter a retracement within a confirmed trend | Trend reversal during pullback; sparse opportunities | Partial components used by prototype, not independently tested |
| Mean reversion | Fade extremes when range regime is stable | Large losses during persistent trend/high volatility | No |
| Hybrid/regime switch | Select trend or range logic based on regime | Regime misclassification and parameter instability | Regime labels only; no validated switching rules |

## Selection decision

None. The implemented trend-pullback is only a buildable/testable candidate, not an evidence-based “best” strategy. No claims about win rate, profitability, robustness, or accuracy are made. See [strategy specification](../../docs/STRATEGY.md) and [validation status](../../docs/VALIDATION.md).
