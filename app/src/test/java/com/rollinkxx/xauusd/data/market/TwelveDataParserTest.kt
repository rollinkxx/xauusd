package com.rollinkxx.xauusd.data.market

import org.junit.Assert.*
import org.junit.Test

/** Protocol-only JSON fixtures for parser tests; these are not market data and are never used by the app. */
class TwelveDataParserTest {
    @Test fun parsesUtcCandleDataAndDeduplicates() {
        val body = """{"meta":{"symbol":"XAU/USD","interval":"5min"},"values":[{"datetime":"2026-01-01 00:05:00","open":"2001","high":"2003","low":"2000","close":"2002","volume":"0"},{"datetime":"2026-01-01 00:00:00","open":"2000","high":"2002","low":"1999","close":"2001","volume":"0"},{"datetime":"2026-01-01 00:00:00","open":"2000","high":"2002","low":"1999","close":"2001","volume":"0"}]}"""
        val snapshot = TwelveDataParser.parse(body, receivedAt = 1_767_225_900L, latencyMs = 37)
        assertEquals(2, snapshot.candles.size)
        assertEquals("XAU/USD", snapshot.quote.symbol)
        assertEquals(2_002.0, snapshot.quote.last, 0.0)
        assertEquals(1_767_225_900L, snapshot.quote.receivedAt)
        assertEquals(37L, snapshot.quote.latencyMs)
        assertEquals(snapshot.candles.sortedBy { it.timestamp }, snapshot.candles)
    }

    @Test fun malformedOrInvalidRowsNeverBecomePrice() {
        assertThrows(MarketDataException::class.java) { TwelveDataParser.parse("not-json", 10, 1) }
        assertThrows(MarketDataException::class.java) { TwelveDataParser.parse("{}", 10, 1) }
        val badRows = """{"meta":{"symbol":"XAU/USD"},"values":[{"datetime":"bad","open":"1","high":"1","low":"1","close":"1"},{"datetime":"2026-01-01 00:05:00","open":"2","high":"1","low":"1","close":"2"}]}"""
        assertThrows(MarketDataException::class.java) { TwelveDataParser.parse(badRows, 10, 1) }
    }

    @Test fun providerErrorAndWrongSymbolAreRejected() {
        assertThrows(MarketDataException::class.java) { TwelveDataParser.parse("""{"status":"error","code":401,"message":"unauthorized"}""", 10, 1) }
        assertThrows(MarketDataException::class.java) {
            TwelveDataParser.parse("""{"meta":{"symbol":"EUR/USD"},"values":[]}""", 10, 1)
        }
    }
}
