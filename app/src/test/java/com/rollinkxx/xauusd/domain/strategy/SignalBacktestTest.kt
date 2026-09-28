package com.rollinkxx.xauusd.domain.strategy

import com.rollinkxx.xauusd.domain.model.Candle
import com.rollinkxx.xauusd.domain.model.Direction
import org.junit.Assert.*
import org.junit.Test

class SignalBacktestTest {
    @Test fun insufficientHistoryReturnsWaitWithoutAProbabilityClaim() {
        val dataTestBars = (0 until 40).map { i -> Candle((i + 1) * 300L, 2000.0, 2001.0, 1999.0, 2000.0 + i * 0.01) }
        val signal = SignalEngine().evaluate(dataTestBars, 50_000L)
        assertEquals(Direction.WAIT, signal.direction)
        assertTrue(signal.reasons.first().contains("Insufficient"))
        assertNull(signal.entry)
    }

    @Test fun backtestWithInsufficientHistoryDoesNotInventTradesOrMetrics() {
        val dataTestBars = (0 until 100).map { i -> Candle((i + 1) * 300L, 2000.0, 2001.0, 1999.0, 2000.0) }
        val result = Backtester().run(dataTestBars)
        assertTrue(result.trades.isEmpty())
        assertEquals(0, result.metrics.totalTrades)
        assertNull(result.metrics.winRate)
        assertNull(result.metrics.profitFactor)
        assertNull(result.metrics.expectancy)
    }
}
