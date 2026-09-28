package com.rollinkxx.xauusd.domain.paper

import com.rollinkxx.xauusd.domain.model.Candle
import com.rollinkxx.xauusd.domain.model.Direction
import com.rollinkxx.xauusd.domain.model.PaperPosition
import com.rollinkxx.xauusd.domain.model.TradeRecord
import kotlin.math.abs

object PaperTradingEngine {
    fun quantityOz(equity: Double, riskPercent: Double, entry: Double, stop: Double): Double {
        require(equity > 0 && riskPercent > 0 && riskPercent <= 100 && entry > 0 && stop > 0)
        val distance = abs(entry - stop)
        require(distance > 0)
        return equity * riskPercent / 100.0 / distance
    }

    fun entryFill(direction: Direction, price: Double, spread: Double, slippage: Double): Double {
        require(direction != Direction.WAIT && price > 0 && spread >= 0 && slippage >= 0)
        val halfSpread = spread / 2.0
        return if (direction == Direction.BUY) price + halfSpread + slippage else price - halfSpread - slippage
    }

    fun exitFill(direction: Direction, price: Double, spread: Double, slippage: Double): Double {
        require(direction != Direction.WAIT && price > 0 && spread >= 0 && slippage >= 0)
        val halfSpread = spread / 2.0
        return if (direction == Direction.BUY) price - halfSpread - slippage else price + halfSpread + slippage
    }

    /** If both levels are touched within one OHLC candle, stop loss wins (conservative assumption). */
    fun exitReason(position: PaperPosition, candle: Candle): String? {
        val stopHit = if (position.direction == Direction.BUY) candle.low <= position.stopLoss else candle.high >= position.stopLoss
        val targetHit = if (position.direction == Direction.BUY) candle.high >= position.takeProfit else candle.low <= position.takeProfit
        return when { stopHit -> "Stop loss"; targetHit -> "Take profit"; else -> null }
    }

    fun close(position: PaperPosition, rawExit: Double, closedAt: Long, reason: String): TradeRecord {
        val exit = exitFill(position.direction, rawExit, position.spreadAssumption, position.slippageAssumption)
        val rawEntry = position.entryMarketPrice ?: position.entryPrice
        val gross = if (position.direction == Direction.BUY) (rawExit - rawEntry) * position.quantityOz
            else (rawEntry - rawExit) * position.quantityOz
        val net = if (position.direction == Direction.BUY) (exit - position.entryPrice) * position.quantityOz
            else (position.entryPrice - exit) * position.quantityOz
        return TradeRecord(position.id, position.direction, position.entryPrice, exit, position.quantityOz,
            position.openedAt, closedAt, gross, gross - net, net, reason, position.signalScore,
            position.strategyVersion, position.regime, position.provider)
    }
}
