package com.rollinkxx.xauusd.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rollinkxx.xauusd.core.security.SecretVault
import com.rollinkxx.xauusd.data.local.AppPreferences
import com.rollinkxx.xauusd.data.local.PaperStore
import com.rollinkxx.xauusd.data.market.MarketDataException
import com.rollinkxx.xauusd.data.market.MarketDataProvider
import com.rollinkxx.xauusd.data.market.TwelveDataProvider
import com.rollinkxx.xauusd.domain.model.Direction
import com.rollinkxx.xauusd.domain.model.FeedState
import com.rollinkxx.xauusd.domain.model.FeedStatus
import com.rollinkxx.xauusd.domain.model.MarketQuote
import com.rollinkxx.xauusd.domain.model.PaperPosition
import com.rollinkxx.xauusd.domain.model.Signal
import com.rollinkxx.xauusd.domain.model.TradeRecord
import com.rollinkxx.xauusd.domain.paper.PaperTradingEngine
import com.rollinkxx.xauusd.domain.strategy.MarketMath
import com.rollinkxx.xauusd.domain.strategy.SignalEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs

 data class MarketUiState(
    val feed: FeedStatus = FeedStatus(),
    val quote: MarketQuote? = null,
    val candles: List<com.rollinkxx.xauusd.domain.model.Candle> = emptyList(),
    val signal: Signal = Signal(Direction.WAIT, 0, listOf("Connect a market-data provider to calculate signals."), "Insufficient Data", timestamp = 0, fingerprint = "none"),
    val positions: List<PaperPosition> = emptyList(),
    val history: List<TradeRecord> = emptyList(),
    val startingBalance: Double = 10_000.0,
    val realizedPnl: Double = 0.0,
    val openPnl: Double = 0.0,
    val riskPercent: Double = 1.0,
    val spread: Double = 0.30,
    val slippage: Double = 0.05,
    val minimumScore: Int = 70,
    val minimumRiskReward: Double = 1.5,
    val autoPaperTrading: Boolean = true,
    val apiKeyConfigured: Boolean = false,
    val isConnecting: Boolean = false,
    val statusMessage: String = "",
    val lastUpdatedAt: Long? = null,
)

class MarketViewModel(application: Application) : AndroidViewModel(application) {
    private val vault = SecretVault(application)
    private val prefs = AppPreferences(application)
    private val store = PaperStore(application)
    private val provider: MarketDataProvider = TwelveDataProvider()
    private val refreshMutex = Mutex()
    private var monitoringJob: Job? = null
    @Volatile private var apiKey: String? = null
    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(startingBalance = prefs.startingBalance, riskPercent = prefs.riskPercent,
            spread = prefs.spread, slippage = prefs.slippage, minimumScore = prefs.minimumScore,
            minimumRiskReward = prefs.minimumRiskReward, autoPaperTrading = prefs.autoPaperTrading) }
        viewModelScope.launch(Dispatchers.IO) {
            val key = runCatching { vault.loadApiKey() }.getOrElse { error ->
                _state.update { it.copy(statusMessage = error.message ?: "Stored API key could not be read.") }; null
            }
            apiKey = key
            val positions = runCatching { store.positions() }.getOrDefault(emptyList())
            val history = runCatching { store.history() }.getOrDefault(emptyList())
            _state.update { it.copy(apiKeyConfigured = !key.isNullOrBlank(), positions = positions, history = history,
                realizedPnl = history.sumOf { trade -> trade.netPnl }, statusMessage = if (key.isNullOrBlank()) "Add a Twelve Data key to connect. No market data is simulated." else it.statusMessage) }
        }
    }

    fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        monitoringJob = viewModelScope.launch {
            var backoff = 60_000L
            while (true) {
                if (apiKey.isNullOrBlank()) {
                    _state.update { it.copy(feed = FeedStatus(FeedState.NOT_CONFIGURED, provider.name, message = "API key required; no market data is being shown.")) }
                    delay(60_000L)
                } else {
                    val success = refreshOnce()
                    backoff = if (success) 300_000L else (backoff * 2).coerceAtMost(30 * 60_000L)
                    delay(if (success) 300_000L else backoff)
                }
            }
        }
    }

    fun stopMonitoring() { monitoringJob?.cancel(); monitoringJob = null }

    fun saveApiKey(value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                vault.saveApiKey(value.trim())
                apiKey = value.trim().takeIf { it.isNotBlank() }
                _state.update { it.copy(apiKeyConfigured = apiKey != null, statusMessage = if (apiKey == null) "API key removed." else "Key stored encrypted on this device. Run Connection Test.") }
                if (apiKey == null) _state.update { it.copy(feed = FeedStatus(FeedState.NOT_CONFIGURED, provider.name, message = "API key required.")) }
                else testConnection()
            } catch (e: Exception) {
                _state.update { it.copy(statusMessage = e.message ?: "Unable to store API key securely.") }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            if (apiKey.isNullOrBlank()) { _state.update { it.copy(statusMessage = "Enter and save your own provider key first.") }; return@launch }
            _state.update { it.copy(isConnecting = true, feed = FeedStatus(FeedState.CONNECTING, provider.name, message = "Testing Twelve Data connection…")) }
            val result = runCatching { provider.fetchSnapshot(apiKey!!) }
            result.onSuccess { snapshot ->
                _state.update { it.copy(isConnecting = false, feed = statusFor(snapshot.quote, "Connection test passed; refreshed with real provider data."), quote = snapshot.quote, candles = MarketMath.cleanCandles(snapshot.candles), lastUpdatedAt = snapshot.quote.receivedAt, statusMessage = "Connection test passed. These are provider-returned data, not a simulated feed.") }
            }.onFailure { error ->
                _state.update { it.copy(isConnecting = false, feed = FeedStatus(FeedState.ERROR, provider.name, message = safeMessage(error)), statusMessage = safeMessage(error)) }
            }
        }
    }

    fun updateSettings(risk: Double, spread: Double, slippage: Double, threshold: Int, rr: Double, auto: Boolean) {
        if (risk !in 0.25..2.0 || spread !in 0.0..100.0 || slippage !in 0.0..100.0 || threshold !in 1..100 || rr !in 1.0..5.0) {
            _state.update { it.copy(statusMessage = "Settings are outside the supported safe range.") }; return
        }
        prefs.riskPercent = risk; prefs.spread = spread; prefs.slippage = slippage
        prefs.minimumScore = threshold; prefs.minimumRiskReward = rr; prefs.autoPaperTrading = auto
        _state.update { it.copy(riskPercent = risk, spread = spread, slippage = slippage, minimumScore = threshold, minimumRiskReward = rr, autoPaperTrading = auto, statusMessage = "Settings saved on this device.") }
    }

    fun resetAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.reset() }.onSuccess {
                prefs.startingBalance = 10_000.0
                prefs.lastHandledSignal = null
                _state.update { it.copy(startingBalance = 10_000.0, realizedPnl = 0.0, openPnl = 0.0, positions = emptyList(), history = emptyList(), statusMessage = "Paper account reset.") }
            }.onFailure { _state.update { it.copy(statusMessage = "Account reset failed: ${it.message}") } }
        }
    }

    private suspend fun refreshOnce(): Boolean = refreshMutex.withLock {
        val key = apiKey ?: return false
        _state.update { it.copy(feed = FeedStatus(FeedState.CONNECTING, provider.name, message = "Requesting current XAU/USD 5-minute data…")) }
        return try {
            val snapshot = provider.fetchSnapshot(key)
            val candles = MarketMath.cleanCandles(snapshot.candles)
            val now = System.currentTimeMillis() / 1000L
            val signal = SignalEngine(_state.value.minimumScore, _state.value.minimumRiskReward).evaluate(candles, now)
            var positions = store.positions()
            var history = store.history()
            val marketBar = candles.last()
            val closing = positions.mapNotNull { p ->
                // The candle used to enter the position contains pre-entry price action; stale bars cannot trigger exits.
                if (marketBar.timestamp <= p.marketDataAt || now - snapshot.quote.marketTimestamp > 900L) return@mapNotNull null
                val reason = PaperTradingEngine.exitReason(p, marketBar) ?: return@mapNotNull null
                val rawExit = when {
                    reason == "Stop loss" && p.direction == Direction.BUY -> minOf(p.stopLoss, marketBar.open)
                    reason == "Stop loss" -> maxOf(p.stopLoss, marketBar.open)
                    reason == "Take profit" -> p.takeProfit
                    else -> snapshot.quote.last
                }
                PaperTradingEngine.close(p, rawExit, now, reason)
            }
            closing.forEach { store.close(it) }
            if (closing.isNotEmpty()) { positions = store.positions(); history = store.history() }
            val age = now - snapshot.quote.marketTimestamp
            val status = if (age > 900) FeedStatus(FeedState.STALE, provider.name, snapshot.quote.receivedAt, snapshot.quote.latencyMs, "STALE DATA: latest provider candle is ${age / 60} min old.") else statusFor(snapshot.quote, "Real provider data received; 5-minute candles only.")
            if (status.state == FeedState.LIVE && _state.value.autoPaperTrading && signal.direction != Direction.WAIT && signal.score >= _state.value.minimumScore && positions.isEmpty() && prefs.lastHandledSignal != signal.fingerprint && canOpenTrade(history, _state.value.startingBalance + history.sumOf { it.netPnl }, now)) {
                val entry = PaperTradingEngine.entryFill(signal.direction, snapshot.quote.last, _state.value.spread, _state.value.slippage)
                val risk = abs((signal.entry ?: entry) - (signal.stopLoss ?: entry))
                val quantity = if (risk > 0) PaperTradingEngine.quantityOz(_state.value.startingBalance + history.sumOf { it.netPnl}, _state.value.riskPercent, entry, if (signal.direction == Direction.BUY) entry - risk else entry + risk) else 0.0
                if (quantity > 0 && signal.stopLoss != null && signal.takeProfit != null) {
                    val offsetSL = abs(signal.entry!! - signal.stopLoss)
                    val offsetTP = abs(signal.takeProfit - signal.entry)
                    val position = PaperPosition(UUID.randomUUID().toString(), signal.direction, entry,
                        if (signal.direction == Direction.BUY) entry - offsetSL else entry + offsetSL,
                        if (signal.direction == Direction.BUY) entry + offsetTP else entry - offsetTP,
                        quantity, now, snapshot.quote.marketTimestamp, signal.score, regime = signal.regime,
                        provider = provider.name, signalId = signal.fingerprint, spreadAssumption = _state.value.spread,
                        slippageAssumption = _state.value.slippage, entryMarketPrice = snapshot.quote.last)
                    runCatching { store.insert(position) }.onSuccess { prefs.lastHandledSignal = signal.fingerprint; positions = store.positions() }
                }
            }
            val realized = history.sumOf { it.netPnl }
            val openPnl = positions.sumOf { p -> unrealized(p, snapshot.quote.last) }
            _state.update { it.copy(feed = status, quote = snapshot.quote, candles = candles, signal = signal,
                positions = positions, history = history, realizedPnl = realized, openPnl = openPnl,
                lastUpdatedAt = snapshot.quote.receivedAt, statusMessage = if (status.state == FeedState.STALE) status.message else "") }
            true
        } catch (error: Exception) {
            _state.update { it.copy(feed = FeedStatus(FeedState.ERROR, provider.name, it.lastUpdatedAt, message = safeMessage(error)), statusMessage = safeMessage(error)) }
            false
        }
    }

    private fun canOpenTrade(history: List<TradeRecord>, equity: Double, now: Long): Boolean {
        val dayStart = now - 86_400L
        val dailyNet = history.filter { it.closedAt >= dayStart }.sumOf { it.netPnl }
        if (dailyNet <= -equity * 0.02) return false
        val lastLoss = history.filter { it.netPnl < 0 }.maxOfOrNull { it.closedAt }
        return lastLoss == null || now - lastLoss >= 30 * 60L
    }

    private fun unrealized(p: PaperPosition, last: Double): Double {
        val exit = PaperTradingEngine.exitFill(p.direction, last, p.spreadAssumption, p.slippageAssumption)
        return if (p.direction == Direction.BUY) (exit - p.entryPrice) * p.quantityOz else (p.entryPrice - exit) * p.quantityOz
    }

    private fun statusFor(quote: MarketQuote, message: String): FeedStatus {
        val age = System.currentTimeMillis() / 1000L - quote.marketTimestamp
        val state = if (age > 900) FeedState.STALE else FeedState.LIVE
        return FeedStatus(state, provider.name, quote.receivedAt, quote.latencyMs, if (state == FeedState.STALE) "STALE DATA: latest provider candle is ${age / 60} min old." else message)
    }

    private fun safeMessage(error: Throwable): String = when (error) {
        is MarketDataException -> error.message ?: "Provider request failed."
        else -> error.message?.take(200) ?: "Provider request failed. Check network and API plan."
    }

    override fun onCleared() { stopMonitoring(); store.close(); super.onCleared() }
}
