package com.rollinkxx.xauusd.domain.strategy

import com.rollinkxx.xauusd.domain.model.Candle
import com.rollinkxx.xauusd.domain.model.Direction
import com.rollinkxx.xauusd.domain.model.PaperPosition
import com.rollinkxx.xauusd.domain.model.TradeRecord
import com.rollinkxx.xauusd.domain.paper.PaperTradingEngine
import java.util.UUID

data class BacktestMetrics(
    val totalTrades: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double?,
    val netProfit: Double,
    val profitFactor: Double?,
    val expectancy: Double?,
    val maxDrawdown: Double,
)
data class BacktestResult(val trades: List<TradeRecord>, val metrics: BacktestMetrics)

/** Candidate strategy research only; results are not a promise of live performance. */
class Backtester(private val strategy: SignalEngine = SignalEngine(), private val spread: Double = 0.30, private val slippage: Double = 0.05) {
    fun run(input: List<Candle>, startingBalance: Double = 10_000.0, riskPercent: Double = 1.0): BacktestResult {
        require(startingBalance > 0 && riskPercent in 0.0..100.0)
        val candles = MarketMath.cleanCandles(input)
        val closed = mutableListOf<TradeRecord>()
        var balance = startingBalance
        var peak = startingBalance
        var maxDrawdown = 0.0
        var position: PaperPosition? = null
        for (i in 260 until candles.size) {
            val candle = candles[i]
            val open = position
            if (open != null) {
                val reason = PaperTradingEngine.exitReason(open, candle)
                if (reason != null) {
                    // Gap through a stop is filled at the worse of the stop or the bar open.
                    val rawExit = if (reason == "Stop loss") {
                        if (open.direction == Direction.BUY) minOf(open.stopLoss, candle.open) else maxOf(open.stopLoss, candle.open)
                    } else open.takeProfit
                    val trade = PaperTradingEngine.close(open, rawExit, candle.timestamp, reason)
                    closed += trade
                    balance += trade.netPnl
                    position = null
                }
            }
            if (position == null && i > 260) {
                // Signals see only bars before this bar. Execution uses this bar's open to avoid close-price look-ahead.
                val signal = strategy.evaluate(candles.subList(0, i), candle.timestamp)
                if (signal.direction != Direction.WAIT && signal.entry != null && signal.stopLoss != null && signal.takeProfit != null) {
                    val direction = signal.direction
                    val fill = PaperTradingEngine.entryFill(direction, candle.open, spread, slippage)
                    val stopOffset = kotlin.math.abs(signal.entry - signal.stopLoss)
                    val targetOffset = kotlin.math.abs(signal.takeProfit - signal.entry)
                    val stop = if (direction == Direction.BUY) fill - stopOffset else fill + stopOffset
                    val target = if (direction == Direction.BUY) fill + targetOffset else fill - targetOffset
                    val quantity = PaperTradingEngine.quantityOz(balance, riskPercent, fill, stop)
                    position = PaperPosition(UUID.randomUUID().toString(), direction, fill, stop, target, quantity,
                        candle.timestamp, candles[i - 1].timestamp, signal.score, regime = signal.regime,
                        provider = "Historical fixture/data", signalId = signal.fingerprint,
                        spreadAssumption = spread, slippageAssumption = slippage, entryMarketPrice = candle.open)
                }
            }
            peak = maxOf(peak, balance)
            maxDrawdown = maxOf(maxDrawdown, peak - balance)
        }
        val wins = closed.count { it.netPnl > 0 }
        val losses = closed.count { it.netPnl < 0 }
        val grossWin = closed.filter { it.netPnl > 0 }.sumOf { it.netPnl }
        val grossLoss = -closed.filter { it.netPnl < 0 }.sumOf { it.netPnl }
        return BacktestResult(closed, BacktestMetrics(closed.size, wins, losses,
            closed.takeIf { it.isNotEmpty() }?.let { wins.toDouble() / it.size }, closed.sumOf { it.netPnl },
            if (grossLoss > 0) grossWin / grossLoss else null,
            closed.takeIf { it.isNotEmpty() }?.let { it.sumOf(TradeRecord::netPnl) / it.size }, maxDrawdown))
    }
}
