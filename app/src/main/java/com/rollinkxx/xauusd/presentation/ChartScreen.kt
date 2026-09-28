package com.rollinkxx.xauusd.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rollinkxx.xauusd.domain.model.Candle
import com.rollinkxx.xauusd.domain.strategy.MarketMath
import java.util.Locale

@Composable
fun ChartScreen(state: MarketUiState) {
    var timeframe by remember { mutableIntStateOf(5) }
    val bars = remember(state.candles, timeframe) {
        when (timeframe) { 5 -> MarketMath.cleanCandles(state.candles).takeLast(120); 15 -> MarketMath.aggregate(state.candles, 15).takeLast(120); 60 -> MarketMath.aggregate(state.candles, 60).takeLast(120); else -> MarketMath.aggregate(state.candles, 240).takeLast(120) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("XAU/USD · provider candles", color = Color.White, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5 to "5m", 15 to "15m", 60 to "1h", 240 to "4h").forEach { (mins, label) ->
                FilterChip(selected = timeframe == mins, onClick = { timeframe = mins }, label = { Text(label) })
            }
        }
        Text("${bars.size} candles · OHLC aggregated from the provider's 5-minute series. Empty data is never replaced with a mock chart.", color = Color(0xFF9AA9B5), fontSize = 11.sp)
        if (bars.isEmpty()) {
            Surface(color = Color(0xFF141C23), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(300.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.Center) { Text("Chart unavailable", color = Color.White); Text("Configure a valid Twelve Data key and plan to load real candles.", color = Color(0xFF9AA9B5)) }
            }
        } else {
            CandleChart(bars, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun CandleChart(candles: List<Candle>, modifier: Modifier = Modifier) {
    val up = Color(0xFF40D6A7)
    val down = Color(0xFFFF6B72)
    Canvas(modifier.background(Color(0xFF141C23), RoundedCornerShape(16.dp)).padding(12.dp)) {
        val minPrice = candles.minOf { it.low }
        val maxPrice = candles.maxOf { it.high }
        val range = (maxPrice - minPrice).takeIf { it > 0.0 } ?: maxPrice * 0.001
        val xGap = size.width / candles.size.coerceAtLeast(1)
        val candleWidth = (xGap * 0.56f).coerceIn(2f, 12f)
        fun y(price: Double) = size.height - (((price - minPrice) / range).toFloat() * size.height)
        repeat(4) { i ->
            val gy = size.height * i / 3f
            drawLine(Color(0xFF26323C), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        }
        candles.forEachIndexed { index, candle ->
            val x = (index + 0.5f) * xGap
            val color = if (candle.close >= candle.open) up else down
            drawLine(color, Offset(x, y(candle.high)), Offset(x, y(candle.low)), strokeWidth = 1.5f)
            val top = y(maxOf(candle.open, candle.close))
            val bottom = y(minOf(candle.open, candle.close))
            val height = (bottom - top).coerceAtLeast(2f)
            drawRect(color, topLeft = Offset(x - candleWidth / 2, top), size = androidx.compose.ui.geometry.Size(candleWidth, height))
        }
        val last = candles.last().close
        drawLine(Color(0xFF74A9FF), Offset(0f, y(last)), Offset(size.width, y(last)), strokeWidth = 1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    }
}
