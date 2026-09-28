package com.rollinkxx.xauusd.data.market

import com.rollinkxx.xauusd.domain.model.MarketProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object GoldApiProtocol {
    const val PRICE_URL = "https://api.gold-api.com/price/XAU/USD"
    const val MINIMUM_CACHE_SECONDS = 30L
}

class GoldApiProvider : MarketDataProvider {
    override val id: MarketProviderId = MarketProviderId.GOLD_API_LIVE
    override val requiresApiKey: Boolean = false
    override val suppliesHistoricalCandles: Boolean = false

    override suspend fun fetchSnapshot(apiKey: String): MarketSnapshot = withContext(Dispatchers.IO) {
        // The provider's public current-price endpoint requires no credential. Do not transmit any key.
        val connection = (URL(GoldApiProtocol.PRICE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            useCaches = true
        }
        val started = System.nanoTime()
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw MarketDataException("Gold API returned an empty XAU/USD response.", code)
            if (code !in 200..299) throw MarketDataException("Gold API quote request failed (HTTP $code).", code)
            val latency = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L)
            GoldApiParser.parseCurrent(body, System.currentTimeMillis() / 1000L, latency)
        } catch (error: MarketDataException) {
            throw error
        } catch (error: Exception) {
            throw MarketDataException(error.message ?: "Gold API quote request failed.")
        } finally {
            connection.disconnect()
        }
    }
}
