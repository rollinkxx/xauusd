package com.rollinkxx.xauusd.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rollinkxx.xauusd.domain.model.Direction
import com.rollinkxx.xauusd.domain.model.FeedState
import com.rollinkxx.xauusd.domain.model.MarketProviderId
import com.rollinkxx.xauusd.domain.model.PaperPosition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF0B1015)
private val Panel = Color(0xFF141C23)
private val Mint = Color(0xFF40D6A7)
private val Red = Color(0xFFFF6B72)
private val Muted = Color(0xFF9AA9B5)

private enum class Page(val title: String) { DASHBOARD("Home"), CHART("Chart"), POSITIONS("Positions"), HISTORY("History"), STATS("Stats"), SETTINGS("Settings") }

@Composable
fun XauApp(model: MarketViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(Page.DASHBOARD) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Ink, surface = Panel, onBackground = Color(0xFFE7EEF3), onSurface = Color(0xFFE7EEF3), secondary = Color(0xFF74A9FF))) {
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Color.White) {
                Page.entries.forEach { item ->
                    NavigationBarItem(selected = page == item, onClick = { page = item },
                        icon = { Text(item.title.take(1), fontWeight = FontWeight.Bold) },
                        label = { Text(item.title, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, indicatorColor = Mint, selectedTextColor = Mint, unselectedTextColor = Muted))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("XAUUSD SIGNAL LAB", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                        Text(page.title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    FeedPill(state.feed.state)
                }
                when (page) {
                    Page.DASHBOARD -> Dashboard(state, onSettings = { page = Page.SETTINGS })
                    Page.CHART -> ChartScreen(state)
                    Page.POSITIONS -> PositionsScreen(state)
                    Page.HISTORY -> HistoryScreen(state)
                    Page.STATS -> StatsScreen(state)
                    Page.SETTINGS -> SettingsScreen(state, model)
                }
            }
        }
    }
}

@Composable
private fun FeedPill(status: FeedState) {
    val color = when (status) { FeedState.LIVE -> Mint; FeedState.STALE -> Color(0xFFFFBD69); FeedState.ERROR, FeedState.OFFLINE -> Red; else -> Muted }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(99.dp)) {
        Text(if (status == FeedState.NOT_CONFIGURED) "OFFLINE" else status.name.replace('_', ' '), color = color, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PageScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun PanelCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(Locale.US), fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable
private fun Dashboard(state: MarketUiState, onSettings: () -> Unit) {
    PageScroll {
        PanelCard("${state.quote?.symbol ?: state.selectedProvider.instrumentLabel} · ${if (state.selectedProvider.suppliesHistoricalCandles) "5-minute feed" else "live quote"}") {
            val quote = state.quote
            Text(quote?.last?.let { "$" + money(it) } ?: "—", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("Last · USD per troy ounce", fontSize = 13.sp, color = Muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Bid", quote?.bid?.let { money(it) } ?: "Not supplied")
                Metric("Ask", quote?.ask?.let { money(it) } ?: "Not supplied")
                Metric("Source", quote?.provider ?: "—")
            }
            HorizontalDivider(color = Color(0xFF26323C))
            Text(state.feed.message.ifBlank { "Waiting for provider data." }, color = if (state.feed.state == FeedState.ERROR || state.feed.state == FeedState.STALE) Red else Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Market time", quote?.marketTimestamp?.let(::dateTime) ?: "—")
                Metric("Age", quote?.let { "${((System.currentTimeMillis() / 1000L - it.marketTimestamp).coerceAtLeast(0)) / 60} min" } ?: "—")
                Metric("Latency", quote?.let { "${it.latencyMs} ms" } ?: "—")
            }
            if (state.quote == null) Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Configure market data") }
        }
        PanelCard("Signal · candidate strategy") {
            val signal = state.signal
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(signal.direction.name, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = when (signal.direction) { Direction.BUY -> Mint; Direction.SELL -> Red; else -> Color.White })
                Text("Score ${signal.score}/100", color = Muted, fontSize = 13.sp)
            }
            Text("Regime: ${signal.regime}", color = Muted, fontSize = 13.sp)
            signal.reasons.forEach { Text("•  $it", fontSize = 13.sp) }
            if (signal.direction != Direction.WAIT && signal.entry != null) {
                HorizontalDivider(color = Color(0xFF26323C))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Entry", money(signal.entry)); Metric("Stop", signal.stopLoss?.let(::money) ?: "—"); Metric("Target", signal.takeProfit?.let(::money) ?: "—")
                }
                Text("Signal score is a rule score, not a win probability.", color = Muted, fontSize = 11.sp)
            }
        }
        PanelCard("Paper account") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Balance", dollars(state.startingBalance + state.realizedPnl))
                Metric("Open P/L", dollars(state.openPnl), if (state.openPnl >= 0) Mint else Red)
                Metric("Equity", dollars(state.startingBalance + state.realizedPnl + state.openPnl))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Active positions: ${state.positions.size}", color = Muted, fontSize = 12.sp)
                Text(if (state.autoPaperTrading) "Auto paper: ON" else "Auto paper: OFF", color = if (state.autoPaperTrading) Mint else Muted, fontSize = 12.sp)
            }
        }
        Text("Simulation only · no live orders · past or simulated results do not predict future results.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color = Color.White) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Muted, fontSize = 10.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PositionsScreen(state: MarketUiState) {
    PageScroll {
        PanelCard("Active virtual positions") {
            if (state.positions.isEmpty()) Text("No open positions. New positions require a fresh feed and an eligible signal.", color = Muted)
            state.positions.forEach { p ->
                val matchingQuote = state.quote?.takeIf { it.provider == p.provider }
                PositionCard(p, matchingQuote?.last, state.openPnl, differentProvider = state.quote != null && matchingQuote == null)
            }
            if (state.positions.any { it.provider != state.selectedProvider.displayName }) {
                Text("Positions opened under another provider are retained but not repriced or closed with this feed.", color = Muted, fontSize = 11.sp)
            }
            Text("Paper trading only. No brokerage account or real order is connected.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PositionCard(position: PaperPosition, current: Double?, openPnl: Double, differentProvider: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(position.direction.name, color = if (position.direction == Direction.BUY) Mint else Red, fontWeight = FontWeight.Bold)
            Text("${"%.3f".format(Locale.US, position.quantityOz)} oz", color = Muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Entry", money(position.entryPrice)); Metric("Current", current?.let(::money) ?: if (differentProvider) "Switch provider" else "—"); Metric("Open P/L", dollars(openPnl), if (openPnl >= 0) Mint else Red) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Stop", money(position.stopLoss)); Metric("Target", money(position.takeProfit)); Metric("Score", "${position.signalScore}/100") }
        Text("${position.strategyVersion} · ${position.regime} · ${dateTime(position.openedAt)}", color = Muted, fontSize = 10.sp)
        HorizontalDivider(color = Color(0xFF26323C))
    }
}

@Composable
private fun HistoryScreen(state: MarketUiState) {
    PageScroll {
        PanelCard("Closed paper trades · ${state.history.size}") {
            if (state.history.isEmpty()) Text("No closed trades yet. History is stored locally on this device.", color = Muted)
            state.history.take(100).forEach { trade ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${trade.direction} · ${trade.exitReason}", fontWeight = FontWeight.SemiBold)
                        Text("${dateTime(trade.closedAt)} · ${trade.strategyVersion}", color = Muted, fontSize = 10.sp)
                        Text("Entry ${money(trade.entryPrice)} → Exit ${money(trade.exitPrice)} · ${"%.3f".format(Locale.US, trade.quantityOz)} oz", color = Muted, fontSize = 11.sp)
                    }
                    Text(dollars(trade.netPnl), color = if (trade.netPnl >= 0) Mint else Red, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = Color(0xFF26323C))
            }
        }
    }
}

@Composable
private fun StatsScreen(state: MarketUiState) {
    val trades = state.history
    val wins = trades.count { it.netPnl > 0 }
    val losses = trades.count { it.netPnl < 0 }
    val grossProfit = trades.filter { it.netPnl > 0 }.sumOf { it.netPnl }
    val grossLoss = -trades.filter { it.netPnl < 0 }.sumOf { it.netPnl }
    val pf = if (grossLoss > 0) "%.2f".format(Locale.US, grossProfit / grossLoss) else "N/A"
    PageScroll {
        PanelCard("Live paper performance") {
            Text("Based only on locally recorded virtual trades; backtest and live-paper results are not combined.", color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Total trades", "${trades.size}"); Metric("Wins / losses", "$wins / $losses"); Metric("Win rate", if (trades.isEmpty()) "N/A" else "${"%.1f".format(Locale.US, 100.0 * wins / trades.size)}%") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Net P/L", dollars(trades.sumOf { it.netPnl }), if (trades.sumOf { it.netPnl } >= 0) Mint else Red); Metric("Profit factor", pf); Metric("Expectancy", if (trades.isEmpty()) "N/A" else dollars(trades.sumOf { it.netPnl } / trades.size)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Average win", trades.filter { it.netPnl > 0 }.takeIf { it.isNotEmpty() }?.let { dollars(it.sumOf { t -> t.netPnl } / it.size) } ?: "N/A"); Metric("Average loss", trades.filter { it.netPnl < 0 }.takeIf { it.isNotEmpty() }?.let { dollars(it.sumOf { t -> t.netPnl } / it.size) } ?: "N/A"); Metric("Open positions", "${state.positions.size}") }
            Text("No out-of-sample or walk-forward performance claim is made. Max drawdown/equity curves are not yet reported.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsScreen(state: MarketUiState, model: MarketViewModel) {
    var key by remember(state.selectedProvider) { mutableStateOf("") }
    var risk by remember(state.riskPercent) { mutableStateOf(state.riskPercent.toString()) }
    var spread by remember(state.spread) { mutableStateOf(state.spread.toString()) }
    var slip by remember(state.slippage) { mutableStateOf(state.slippage.toString()) }
    var score by remember(state.minimumScore) { mutableStateOf(state.minimumScore.toString()) }
    var rr by remember(state.minimumRiskReward) { mutableStateOf(state.minimumRiskReward.toString()) }
    var auto by remember(state.autoPaperTrading) { mutableStateOf(state.autoPaperTrading) }
    var confirmReset by remember { mutableStateOf(false) }
    PageScroll {
        PanelCard("Market data provider") {
            Text("Choose a data source. Credentials are encrypted and stored separately per provider.", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.selectedProvider == MarketProviderId.TWELVE_DATA,
                    onClick = { model.selectProvider(MarketProviderId.TWELVE_DATA) }, label = { Text("Twelve Data") })
                FilterChip(selected = state.selectedProvider == MarketProviderId.GOLD_API_LIVE,
                    onClick = { model.selectProvider(MarketProviderId.GOLD_API_LIVE) }, label = { Text("Gold API · no key") })
            }
            Text(state.selectedProvider.instrumentLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(state.selectedProvider.setupDescription, color = Muted, fontSize = 11.sp)
            if (state.selectedProvider.requiresApiKey) {
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(state.selectedProvider.keyLabel) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.saveApiKey(key); key = "" }, modifier = Modifier.weight(1f)) { Text("Save & Test") }
                    OutlinedButton(onClick = { model.testConnection() }, enabled = state.apiKeyConfigured && !state.isConnecting, modifier = Modifier.weight(1f)) { Text(if (state.isConnecting) "Testing…" else "Connection Test") }
                }
                if (state.apiKeyConfigured) OutlinedButton(onClick = { model.saveApiKey("") }, modifier = Modifier.fillMaxWidth()) { Text("Remove stored key") }
            } else {
                OutlinedButton(onClick = { model.testConnection() }, enabled = !state.isConnecting, modifier = Modifier.fillMaxWidth()) { Text(if (state.isConnecting) "Fetching…" else "Fetch live XAU/USD (no key)") }
            }
            if (state.statusMessage.isNotBlank()) Text(state.statusMessage, color = if (state.feed.state == FeedState.ERROR) Red else Muted, fontSize = 11.sp)
            Text(state.feed.message, color = if (state.feed.state == FeedState.ERROR || state.feed.state == FeedState.STALE) Red else Muted, fontSize = 11.sp)
            Text(if (state.selectedProvider.requiresApiKey) "Only this provider receives its own key over HTTPS. Do not reuse keys from other services." else "No key is sent. Cache live-price requests for at least 30 seconds; this source has no historical candles.", color = Muted, fontSize = 11.sp)
        }
        PanelCard("Paper trading assumptions") {
            NumberField("Risk per trade (%) · 0.25–2", risk, { risk = it })
            NumberField("Spread assumption (USD/oz)", spread, { spread = it })
            NumberField("Slippage assumption (USD/oz)", slip, { slip = it })
            NumberField("Minimum signal score · 1–100", score, { score = it }, integer = true)
            NumberField("Minimum risk/reward · 1–5", rr, { rr = it })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text("Auto paper trading", fontWeight = FontWeight.SemiBold); Text("Local simulation only; no real order execution.", color = Muted, fontSize = 11.sp) }
                Switch(checked = auto, onCheckedChange = { auto = it })
            }
            Button(onClick = {
                model.updateSettings(risk.toDoubleOrNull() ?: Double.NaN, spread.toDoubleOrNull() ?: Double.NaN,
                    slip.toDoubleOrNull() ?: Double.NaN, score.toIntOrNull() ?: -1, rr.toDoubleOrNull() ?: Double.NaN, auto)
            }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
            Text("Virtual balance starts at $10,000. Position size is ounces, not broker lots; the provider does not supply your broker contract specification. A 2% daily-loss limit and 30-minute cooldown after a loss are applied.", color = Muted, fontSize = 11.sp)
        }
        PanelCard("Privacy and risk") {
            Text("No login, analytics or advertising. Only the selected provider receives requests when monitoring is active. Trades and settings are stored on this device. Market data is for personal non-commercial use subject to your provider's license.", color = Muted, fontSize = 12.sp)
            Text("Paper trading only. Historical or simulated results do not guarantee future outcomes. News filter: NOT CONFIGURED.", color = Muted, fontSize = 12.sp)
            Text(state.statusMessage.ifBlank { state.feed.message }, color = if (state.feed.state == FeedState.ERROR) Red else Muted, fontSize = 11.sp)
        }
        PanelCard("Paper account") {
            Text("Reset deletes all local open positions and trade history and restores the virtual balance to $10,000.", color = Muted, fontSize = 12.sp)
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset paper account") }
        }
        Text("Provider data attribution: ${state.selectedProvider.attribution}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 18.dp))
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Reset paper account?") },
        text = { Text("This permanently deletes local virtual positions and trade history. It does not affect a broker account.") },
        confirmButton = { TextButton(onClick = { model.resetAccount(); confirmReset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } })
}

@Composable
private fun NumberField(label: String, value: String, change: (String) -> Unit, integer: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

private fun money(value: Double): String = "%.2f".format(Locale.US, value)
private fun dollars(value: Double): String = (if (value >= 0) "+$" else "-$") + money(kotlin.math.abs(value))
private fun dateTime(epochSeconds: Long): String = runCatching { DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(epochSeconds)) }.getOrDefault("—")
