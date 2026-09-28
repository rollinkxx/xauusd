package com.rollinkxx.xauusd.domain.model

import kotlin.math.max
import kotlin.math.min

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double? = null,
) {
    fun isValid(): Boolean = timestamp > 0 && listOf(open, high, low, close).all { it.isFinite() && it > 0.0 } &&
        high >= max(open, close) && low <= min(open, close) && high >= low && (volume == null || volume.isFinite() && volume >= 0.0)
}

data class MarketQuote(
    val symbol: String,
    val provider: String,
    val last: Double,
    val bid: Double? = null,
    val ask: Double? = null,
    val marketTimestamp: Long,
    val receivedAt: Long,
    val latencyMs: Long,
) {
    fun isValid(): Boolean = symbol == "XAU/USD" && last.isFinite() && last > 0.0 &&
        (bid == null || bid.isFinite() && bid > 0.0) && (ask == null || ask.isFinite() && ask > 0.0) &&
        (bid == null || ask == null || ask >= bid) && marketTimestamp > 0 && receivedAt > 0
}

enum class FeedState { NOT_CONFIGURED, CONNECTING, LIVE, STALE, OFFLINE, ERROR }

data class FeedStatus(
    val state: FeedState = FeedState.NOT_CONFIGURED,
    val provider: String = "Twelve Data",
    val lastUpdate: Long? = null,
    val latencyMs: Long? = null,
    val message: String = "API key required; no market data is being shown.",
)

enum class Direction { BUY, SELL, WAIT }

data class Signal(
    val direction: Direction,
    val score: Int,
    val reasons: List<String>,
    val regime: String,
    val entry: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val riskReward: Double? = null,
    val timestamp: Long,
    val fingerprint: String,
) {
    init { require(score in 0..100) }
}

data class PaperPosition(
    val id: String,
    val direction: Direction,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val quantityOz: Double,
    val openedAt: Long,
    val marketDataAt: Long,
    val signalScore: Int,
    val strategyVersion: String = "trend-pullback-v1.0",
    val regime: String,
    val provider: String,
    val signalId: String,
    val spreadAssumption: Double,
    val slippageAssumption: Double,
    val entryMarketPrice: Double? = null,
)

data class TradeRecord(
    val id: String,
    val direction: Direction,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantityOz: Double,
    val openedAt: Long,
    val closedAt: Long,
    val grossPnl: Double,
    val costs: Double,
    val netPnl: Double,
    val exitReason: String,
    val signalScore: Int,
    val strategyVersion: String,
    val regime: String,
    val provider: String,
)
