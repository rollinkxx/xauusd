package com.rollinkxx.xauusd.domain.model

import kotlin.math.max
import kotlin.math.min

enum class MarketProviderId(
    val preferenceKey: String,
    val displayName: String,
    val instrumentLabel: String,
    val keyLabel: String,
    val setupDescription: String,
    val attribution: String,
    val requiresApiKey: Boolean,
    val suppliesHistoricalCandles: Boolean,
) {
    TWELVE_DATA(
        preferenceKey = "twelve_data",
        displayName = "Twelve Data",
        instrumentLabel = "Spot XAU/USD",
        keyLabel = "Twelve Data API key",
        setupDescription = "Spot XAU/USD · 5-minute candles. Your account plan must include commodity and intraday time-series access.",
        attribution = "Source: Twelve Data",
        requiresApiKey = true,
        suppliesHistoricalCandles = true,
    ),
    GOLD_API_LIVE(
        preferenceKey = "gold_api_live",
        displayName = "Gold API",
        instrumentLabel = "Spot XAU/USD",
        keyLabel = "",
        setupDescription = "Current XAU/USD quote, no key. The public endpoint does not provide candle history; signals and paper entries stay disabled until historical candles are available.",
        attribution = "Source: Gold API",
        requiresApiKey = false,
        suppliesHistoricalCandles = false,
    );

    companion object {
        fun fromPreference(value: String?): MarketProviderId =
            if (value == null) GOLD_API_LIVE else entries.firstOrNull { it.preferenceKey == value } ?: TWELVE_DATA
    }
}

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
    fun isValid(): Boolean = symbol.isNotBlank() && provider.isNotBlank() && last.isFinite() && last > 0.0 &&
        (bid == null || bid.isFinite() && bid > 0.0) && (ask == null || ask.isFinite() && ask > 0.0) &&
        (bid == null || ask == null || ask >= bid) && marketTimestamp > 0 && receivedAt > 0
}

enum class FeedState { NOT_CONFIGURED, CONNECTING, LIVE, STALE, OFFLINE, ERROR }

data class FeedStatus(
    val state: FeedState = FeedState.NOT_CONFIGURED,
    val provider: String = MarketProviderId.GOLD_API_LIVE.displayName,
    val lastUpdate: Long? = null,
    val latencyMs: Long? = null,
    val message: String = "No key required for the current XAU/USD quote; historical candles are not included.",
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
