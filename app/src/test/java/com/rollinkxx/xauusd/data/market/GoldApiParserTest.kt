package com.rollinkxx.xauusd.data.market

import com.rollinkxx.xauusd.domain.model.MarketProviderId
import org.junit.Assert.*
import org.junit.Test

class GoldApiParserTest {
    @Test fun parsesCurrentUsdXauQuoteWithoutInventingCandles() {
        val snapshot = GoldApiParser.parseCurrent(
            """{"currency":"USD","currencySymbol":"$","exchangeRate":1.0,"name":"Gold","price":4139.70,"symbol":"XAU","updatedAt":"2026-09-28T17:16:36Z","updatedAtReadable":"a few seconds ago"}""",
            receivedAt = 1_790_000_000L,
            latencyMs = 42L,
        )
        assertEquals("XAU/USD", snapshot.quote.symbol)
        assertEquals(MarketProviderId.GOLD_API_LIVE.displayName, snapshot.quote.provider)
        assertEquals(4139.70, snapshot.quote.last, 0.001)
        assertEquals(1_790_000_000L, snapshot.quote.receivedAt)
        assertEquals(42L, snapshot.quote.latencyMs)
        assertTrue(snapshot.quote.isValid())
        assertTrue(snapshot.candles.isEmpty())
    }

    @Test fun rejectsNonXauAndNonUsdResponses() {
        assertThrows(MarketDataException::class.java) {
            GoldApiParser.parseCurrent("""{"symbol":"XAG","currency":"USD","price":30,"updatedAt":"2026-09-28T17:16:36Z"}""", 100, 1)
        }
        assertThrows(MarketDataException::class.java) {
            GoldApiParser.parseCurrent("""{"symbol":"XAU","currency":"IDR","price":1,"updatedAt":"2026-09-28T17:16:36Z"}""", 100, 1)
        }
    }

    @Test fun rejectsInvalidPriceTimestampAndMalformedJson() {
        assertThrows(MarketDataException::class.java) { GoldApiParser.parseCurrent("no-json", 100, 1) }
        assertThrows(MarketDataException::class.java) {
            GoldApiParser.parseCurrent("""{"symbol":"XAU","currency":"USD","price":0,"updatedAt":"2026-09-28T17:16:36Z"}""", 100, 1)
        }
        assertThrows(MarketDataException::class.java) {
            GoldApiParser.parseCurrent("""{"symbol":"XAU","currency":"USD","price":4000,"updatedAt":"bad-time"}""", 100, 1)
        }
    }

    @Test fun quoteOnlyProviderAdvertisesNoKeyAndNoHistoricalCandles() {
        val provider = GoldApiProvider()
        assertEquals(MarketProviderId.GOLD_API_LIVE, provider.id)
        assertFalse(provider.requiresApiKey)
        assertFalse(provider.suppliesHistoricalCandles)
        assertEquals("https://api.gold-api.com/price/XAU/USD", GoldApiProtocol.PRICE_URL)
        assertEquals(30L, GoldApiProtocol.MINIMUM_CACHE_SECONDS)
    }
}
