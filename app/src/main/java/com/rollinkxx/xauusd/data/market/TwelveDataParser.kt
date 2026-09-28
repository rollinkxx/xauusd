package com.rollinkxx.xauusd.data.market

import com.rollinkxx.xauusd.domain.model.Candle
import com.rollinkxx.xauusd.domain.model.MarketProviderId
import com.rollinkxx.xauusd.domain.model.MarketQuote
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TwelveDataParser {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun parse(body: String, receivedAt: Long, latencyMs: Long): MarketSnapshot {
        val json = try { JSONObject(body) } catch (_: Exception) { throw MarketDataException("Twelve Data returned malformed JSON.") }
        if (json.optString("status") == "error" || json.has("code")) {
            throw MarketDataException(json.optString("message", "Twelve Data request failed.").take(200))
        }
        val symbol = json.optJSONObject("meta")?.optString("symbol")
        if (!symbol.isNullOrBlank() && !symbol.equals("XAU/USD", ignoreCase = true)) {
            throw MarketDataException("Twelve Data response symbol did not match XAU/USD.")
        }
        val values = json.optJSONArray("values") ?: throw MarketDataException("No historical XAU/USD candles were returned.")
        val candles = buildList {
            for (i in 0 until values.length()) {
                val row = values.optJSONObject(i) ?: continue
                try {
                    val timestamp = LocalDateTime.parse(row.getString("datetime"), formatter).toEpochSecond(ZoneOffset.UTC)
                    val candle = Candle(timestamp, row.getString("open").toDouble(), row.getString("high").toDouble(),
                        row.getString("low").toDouble(), row.getString("close").toDouble(), row.optString("volume").toDoubleOrNull())
                    if (candle.isValid()) add(candle)
                } catch (_: Exception) { /* Invalid provider rows are excluded, never repaired with invented values. */ }
            }
        }.distinctBy { it.timestamp }.sortedBy { it.timestamp }.takeLast(5_000)
        if (candles.size < 2) throw MarketDataException("Provider returned fewer than two valid XAU/USD candles.")
        val last = candles.last()
        return MarketSnapshot(MarketQuote("XAU/USD", MarketProviderId.TWELVE_DATA.displayName, last.close,
            marketTimestamp = last.timestamp, receivedAt = receivedAt, latencyMs = latencyMs), candles)
    }
}
