package com.rollinkxx.xauusd.domain.strategy

import com.rollinkxx.xauusd.domain.model.Candle
import com.rollinkxx.xauusd.domain.model.Direction
import com.rollinkxx.xauusd.domain.model.Signal

class SignalEngine(
    private val minScore: Int = 70,
    private val minimumRiskReward: Double = 1.5,
    private val strategyVersion: String = "trend-pullback-v1.0",
) {
    init { require(minScore in 1..100); require(minimumRiskReward > 0) }

    fun evaluate(fiveMinuteInput: List<Candle>, timestamp: Long): Signal {
        val bars = MarketMath.cleanCandles(fiveMinuteInput)
        fun wait(reason: String, regime: String = "Insufficient Data") = Signal(
            Direction.WAIT, 0, listOf(reason), regime, timestamp = timestamp,
            fingerprint = "$strategyVersion:${bars.lastOrNull()?.timestamp ?: timestamp}:WAIT")
        if (bars.size < 260) return wait("Insufficient validated 5-minute history (need at least 260 bars).")

        val fifteen = MarketMath.aggregate(bars, 15)
        val hourly = MarketMath.aggregate(bars, 60)
        val fourHourly = MarketMath.aggregate(bars, 240)
        if (fifteen.size < 20 || hourly.size < 20 || fourHourly.size < 20) {
            return wait("Insufficient higher-timeframe history for confirmation.")
        }
        val closes5 = bars.map { it.close }
        val closes15 = fifteen.map { it.close }
        val closes1h = hourly.map { it.close }
        val closes4h = fourHourly.map { it.close }
        val ema20_5 = MarketMath.ema(closes5, 20).lastOrNull() ?: return wait("EMA unavailable.")
        val ema20_15 = MarketMath.ema(closes15, 20).lastOrNull() ?: return wait("15-minute trend unavailable.")
        val ema20_1h = MarketMath.ema(closes1h, 20).lastOrNull() ?: return wait("1-hour trend unavailable.")
        val ema20_4h = MarketMath.ema(closes4h, 20).lastOrNull() ?: return wait("4-hour trend unavailable.")
        val rsi = MarketMath.rsi(closes15).lastOrNull() ?: return wait("Momentum unavailable.")
        val atr = MarketMath.atr(bars).lastOrNull() ?: return wait("Volatility unavailable.")
        val last = bars.last()
        val highVolatility = atr / last.close > 0.004
        val bull = last.close > ema20_5 && closes15.last() > ema20_15 && closes1h.last() > ema20_1h && closes4h.last() > ema20_4h && rsi in 50.0..70.0
        val bear = last.close < ema20_5 && closes15.last() < ema20_15 && closes1h.last() < ema20_1h && closes4h.last() < ema20_4h && rsi in 30.0..50.0
        val regime = when {
            highVolatility -> "High Volatility"
            bull -> "Trending Bullish"
            bear -> "Trending Bearish"
            else -> "Range / Sideways"
        }
        val direction = when { highVolatility -> Direction.WAIT; bull -> Direction.BUY; bear -> Direction.SELL; else -> Direction.WAIT }
        if (direction == Direction.WAIT) return Signal(direction, 0,
            listOf(if (highVolatility) "ATR/price exceeds the strategy's 0.4% high-volatility guard." else "Multi-timeframe trend and momentum are not aligned."),
            regime, timestamp = timestamp, fingerprint = "$strategyVersion:${last.timestamp}:WAIT")

        val riskDistance = atr * 1.5
        val entry = last.close
        val stop = if (direction == Direction.BUY) entry - riskDistance else entry + riskDistance
        val target = if (direction == Direction.BUY) entry + riskDistance * 2.0 else entry - riskDistance * 2.0
        val rr = kotlin.math.abs(target - entry) / kotlin.math.abs(entry - stop)
        if (rr < minimumRiskReward) return wait("Risk/reward is below the configured minimum.", regime)
        val score = (70 + (if (rsi in 53.0..67.0 || rsi in 33.0..47.0) 10 else 0) +
            (if (!highVolatility) 10 else 0)).coerceIn(0, 100)
        if (score < minScore) return wait("Signal score is below the configured threshold.", regime)
        val reasons = listOf("4-hour and 1-hour trend filters agree.", "15-minute momentum RSI: ${"%.1f".format(java.util.Locale.US, rsi)}.", "5-minute close confirms the trend.", "ATR-based stop and 2R target; assumed R:R ${"%.2f".format(java.util.Locale.US, rr)}.")
        return Signal(direction, score, reasons, regime, entry, stop, target, rr, timestamp,
            "$strategyVersion:${last.timestamp}:$direction")
    }
}
