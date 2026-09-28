package com.rollinkxx.xauusd.data.market

import com.rollinkxx.xauusd.domain.model.Candle
import com.rollinkxx.xauusd.domain.model.MarketProviderId
import com.rollinkxx.xauusd.domain.model.MarketQuote

data class MarketSnapshot(val quote: MarketQuote, val candles: List<Candle>)

interface MarketDataProvider {
    val id: MarketProviderId
    val name: String get() = id.displayName
    val requiresApiKey: Boolean get() = id.requiresApiKey
    val suppliesHistoricalCandles: Boolean get() = id.suppliesHistoricalCandles
    suspend fun fetchSnapshot(apiKey: String): MarketSnapshot
}

class MarketDataException(message: String, val statusCode: Int? = null) : Exception(message)
