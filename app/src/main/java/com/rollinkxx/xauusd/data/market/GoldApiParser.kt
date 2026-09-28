package com.rollinkxx.xauusd.data.market

import com.rollinkxx.xauusd.domain.model.MarketProviderId
import com.rollinkxx.xauusd.domain.model.MarketQuote
import org.json.JSONObject
import java.time.Instant

object GoldApiParser {
    fun parseCurrent(body: String, receivedAt: Long, latencyMs: Long): MarketSnapshot {
        val json = try { JSONObject(body) } catch (_: Exception) {
            throw MarketDataException("Gold API returned malformed quote JSON.")
        }
        val symbol = json.optString("symbol").uppercase()
        val currency = json.optString("currency").uppercase()
        if (symbol != "XAU" || currency != "USD") {
            throw MarketDataException("Gold API response was not XAU priced in USD; received $symbol/$currency.")
        }
        val price = json.optDouble("price", Double.NaN)
        if (!price.isFinite() || price <= 0.0) throw MarketDataException("Gold API returned an invalid XAU/USD price.")
        val timestamp = try { Instant.parse(json.getString("updatedAt")).epochSecond } catch (_: Exception) {
            throw MarketDataException("Gold API response did not include a valid quote timestamp.")
        }
        if (timestamp <= 0L) throw MarketDataException("Gold API returned an invalid XAU/USD timestamp.")
        val quote = MarketQuote(
            symbol = "XAU/USD",
            provider = MarketProviderId.GOLD_API_LIVE.displayName,
            last = price,
            marketTimestamp = timestamp,
            receivedAt = receivedAt,
            latencyMs = latencyMs.coerceAtLeast(0L),
        )
        if (!quote.isValid()) throw MarketDataException("Gold API quote failed validation.")
        return MarketSnapshot(quote = quote, candles = emptyList())
    }
}
