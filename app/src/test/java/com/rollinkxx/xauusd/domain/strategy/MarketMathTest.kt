package com.rollinkxx.xauusd.domain.strategy

import com.rollinkxx.xauusd.domain.model.Candle
import org.junit.Assert.*
import org.junit.Test

class MarketMathTest {
    @Test fun invalidAndDuplicateCandlesAreRejectedAndSorted() {
        val bad = Candle(300, 10.0, 9.0, 8.0, 10.0)
        val first = Candle(600, 10.0, 12.0, 9.0, 11.0)
        val duplicate = Candle(600, 11.0, 13.0, 10.0, 12.0)
        val clean = MarketMath.cleanCandles(listOf(first, bad, duplicate))
        assertEquals(listOf(first), clean)
    }

    @Test fun emaUsesSeedMeanThenRecursiveSmoothing() {
        val series = MarketMath.ema(listOf(1.0, 2.0, 3.0, 4.0), 3)
        assertNull(series[1])
        assertEquals(2.0, series[2]!!, 1e-9)
        assertEquals(3.0, series[3]!!, 1e-9)
    }

    @Test fun rsiHandlesMonotonicAndFlatSeriesWithoutNaN() {
        val up = MarketMath.rsi((0..20).map { it.toDouble() }, 14).last()!!
        val flat = MarketMath.rsi(List(21) { 4.0 }, 14).last()!!
        assertEquals(100.0, up, 1e-9)
        assertEquals(50.0, flat, 1e-9)
        assertTrue(up.isFinite() && flat.isFinite())
    }

    @Test fun atrAndResamplingPreserveOhlcEnvelope() {
        val bars = (0..30).map { i -> Candle((i + 1) * 300L, 100.0 + i, 102.0 + i, 99.0 + i, 101.0 + i) }
        assertTrue(MarketMath.atr(bars).last()!! > 0.0)
        val grouped = MarketMath.aggregate(bars, 15)
        assertEquals(11, grouped.size)
        assertEquals(100.0, grouped.first().open, 1e-9)
        assertEquals(103.0, grouped.first().high, 1e-9)
        assertEquals(99.0, grouped.first().low, 1e-9)
    }
}
