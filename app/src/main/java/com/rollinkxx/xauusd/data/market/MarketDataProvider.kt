package com.rollinkxx.xauusd.data.market

import com.rollinkxx.xauusd.domain.model.Candle
import com.rollinkxx.xauusd.domain.model.MarketQuote

data class MarketSnapshot(val quote: MarketQuote, val candles: List<Candle>)

interface MarketDataProvider {
    val name: String
    suspend fun fetchSnapshot(apiKey: String): MarketSnapshot
}

class MarketDataException(message: String, val statusCode: Int? = null) : Exception(message)
