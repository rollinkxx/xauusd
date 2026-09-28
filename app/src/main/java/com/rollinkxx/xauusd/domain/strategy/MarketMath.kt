package com.rollinkxx.xauusd.domain.strategy

import com.rollinkxx.xauusd.domain.model.Candle
import kotlin.math.abs
import kotlin.math.max

object MarketMath {
    fun cleanCandles(input: List<Candle>, limit: Int = 5_000): List<Candle> {
        require(limit > 0)
        return input.asSequence().filter(Candle::isValid).distinctBy { it.timestamp }
            .sortedBy { it.timestamp }.takeLast(limit).toList()
    }

    fun aggregate(candles: List<Candle>, minutes: Int): List<Candle> {
        require(minutes > 0)
        val span = minutes * 60L
        return cleanCandles(candles).groupBy { Math.floorDiv(it.timestamp, span) * span }
            .toSortedMap().map { (time, items) ->
                Candle(time, items.first().open, items.maxOf { it.high }, items.minOf { it.low }, items.last().close,
                    items.mapNotNull { it.volume }.takeIf { it.isNotEmpty() }?.sum())
            }
    }

    fun ema(values: List<Double>, period: Int): List<Double?> {
        require(period > 0)
        val out = MutableList<Double?>(values.size) { null }
        if (values.size < period) return out
        val multiplier = 2.0 / (period + 1.0)
        var current = values.take(period).average()
        out[period - 1] = current
        for (i in period until values.size) {
            current = (values[i] - current) * multiplier + current
            out[i] = current
        }
        return out
    }

    fun rsi(values: List<Double>, period: Int = 14): List<Double?> {
        require(period > 0)
        val out = MutableList<Double?>(values.size) { null }
        if (values.size <= period) return out
        var gains = 0.0
        var losses = 0.0
        for (i in 1..period) {
            val change = values[i] - values[i - 1]
            if (change >= 0) gains += change else losses -= change
        }
        var avgGain = gains / period
        var avgLoss = losses / period
        fun value(): Double = when {
            avgLoss == 0.0 && avgGain == 0.0 -> 50.0
            avgLoss == 0.0 -> 100.0
            else -> 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        }
        out[period] = value()
        for (i in period + 1 until values.size) {
            val change = values[i] - values[i - 1]
            avgGain = (avgGain * (period - 1) + max(change, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-change, 0.0)) / period
            out[i] = value()
        }
        return out
    }

    fun atr(candles: List<Candle>, period: Int = 14): List<Double?> {
        require(period > 0)
        val out = MutableList<Double?>(candles.size) { null }
        if (candles.size <= period) return out
        val trs = candles.indices.map { i ->
            val c = candles[i]
            if (i == 0) c.high - c.low else max(c.high - c.low,
                max(abs(c.high - candles[i - 1].close), abs(c.low - candles[i - 1].close)))
        }
        var current = trs.subList(1, period + 1).average()
        out[period] = current
        for (i in period + 1 until candles.size) {
            current = (current * (period - 1) + trs[i]) / period
            out[i] = current
        }
        return out
    }
}
