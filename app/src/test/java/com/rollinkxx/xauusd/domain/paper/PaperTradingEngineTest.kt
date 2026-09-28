package com.rollinkxx.xauusd.domain.paper

import com.rollinkxx.xauusd.domain.model.Candle
import com.rollinkxx.xauusd.domain.model.Direction
import com.rollinkxx.xauusd.domain.model.PaperPosition
import org.junit.Assert.*
import org.junit.Test

class PaperTradingEngineTest {
    private fun position(direction: Direction = Direction.BUY) = PaperPosition("t1", direction, 100.2,
        if (direction == Direction.BUY) 98.0 else 102.0, if (direction == Direction.BUY) 104.0 else 96.0,
        10.0, 1_000L, 900L, 75, regime = "Trending", provider = "DATA TEST", signalId = "sig1",
        spreadAssumption = 0.4, slippageAssumption = 0.1, entryMarketPrice = 100.0)

    @Test fun positionSizeUsesEquityRiskAndStopDistance() {
        assertEquals(10.0, PaperTradingEngine.quantityOz(10_000.0, 1.0, 100.0, 90.0), 1e-9)
    }

    @Test fun buyAndSellFillsApplyAdverseSpreadAndSlippage() {
        assertEquals(100.3, PaperTradingEngine.entryFill(Direction.BUY, 100.0, 0.4, 0.1), 1e-9)
        assertEquals(99.7, PaperTradingEngine.exitFill(Direction.BUY, 100.0, 0.4, 0.1), 1e-9)
        assertEquals(99.7, PaperTradingEngine.entryFill(Direction.SELL, 100.0, 0.4, 0.1), 1e-9)
        assertEquals(100.3, PaperTradingEngine.exitFill(Direction.SELL, 100.0, 0.4, 0.1), 1e-9)
    }

    @Test fun sameCandleTouchingBothLevelsExitsAtStop() {
        val bar = Candle(1_200L, 100.0, 105.0, 97.0, 101.0)
        assertEquals("Stop loss", PaperTradingEngine.exitReason(position(), bar))
    }

    @Test fun winningAndLosingTradeNetPnlIncludesCosts() {
        val win = PaperTradingEngine.close(position(), 104.0, 2_000L, "Take profit")
        assertTrue(win.netPnl < win.grossPnl)
        assertEquals(5.0, win.costs, 1e-9)
        val loss = PaperTradingEngine.close(position(), 98.0, 2_000L, "Stop loss")
        assertTrue(loss.netPnl < 0)
    }
}
