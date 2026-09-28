package com.rollinkxx.xauusd.data.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TwelveDataProvider : MarketDataProvider {
    override val name: String = "Twelve Data"

    override suspend fun fetchSnapshot(apiKey: String): MarketSnapshot = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Enter your own Twelve Data API key in Settings." }
        val symbol = URLEncoder.encode("XAU/USD", Charsets.UTF_8.name())
        val url = URL("https://api.twelvedata.com/time_series?symbol=$symbol&interval=5min&outputsize=5000&order=desc&timezone=UTC")
        val started = System.nanoTime()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "apikey $apiKey")
            setRequestProperty("Accept", "application/json")
            useCaches = false
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw MarketDataException("Provider returned an empty response.", code)
            if (code !in 200..299) {
                val details = runCatching { TwelveDataParser.parse(body, System.currentTimeMillis() / 1000, 0) }
                    .exceptionOrNull()?.message ?: "Request failed"
                throw MarketDataException("$details (HTTP $code)".replace(apiKey, "[redacted]"), code)
            }
            val latency = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0)
            TwelveDataParser.parse(body, System.currentTimeMillis() / 1000L, latency)
        } catch (e: MarketDataException) {
            throw MarketDataException((e.message ?: "Provider request failed.").replace(apiKey, "[redacted]"), e.statusCode)
        } catch (e: Exception) {
            throw MarketDataException((e.message ?: "Network request failed.").replace(apiKey, "[redacted]"))
        } finally { connection.disconnect() }
    }
}
