package com.rollinkxx.xauusd.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rollinkxx.xauusd.core.security.SecretVault
import com.rollinkxx.xauusd.data.local.AppPreferences
import com.rollinkxx.xauusd.data.local.PaperStore
import com.rollinkxx.xauusd.data.market.GoldApiProvider
import com.rollinkxx.xauusd.data.market.MarketDataException
import com.rollinkxx.xauusd.data.market.MarketDataProvider
import com.rollinkxx.xauusd.data.market.TwelveDataProvider
import com.rollinkxx.xauusd.domain.model.Direction
import com.rollinkxx.xauusd.domain.model.FeedState
import com.rollinkxx.xauusd.domain.model.FeedStatus
import com.rollinkxx.xauusd.domain.model.MarketProviderId
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
import java.util.UUID
import kotlin.math.abs

data class MarketUiState(
    val selectedProvider: MarketProviderId = MarketProviderId.GOLD_API_LIVE,
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
    private val providers: Map<MarketProviderId, MarketDataProvider> = listOf(TwelveDataProvider(), GoldApiProvider()).associateBy { it.id }
    private val refreshMutex = Mutex()
    private var monitoringJob: Job? = null
    @Volatile private var apiKey: String? = null
    private val _state = MutableStateFlow(MarketUiState(
        selectedProvider = prefs.marketProvider,
        feed = FeedStatus(provider = prefs.marketProvider.displayName),
    ))
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    init {
        // The old app had one ambiguously named key slot. Do not forward it to either provider after upgrade.
        val legacyKeyCleared = runCatching { vault.clearLegacyApiKey() }.getOrDefault(false)
        viewModelScope.launch(Dispatchers.IO) {
            val providerId = prefs.marketProvider
            val activeProvider = providerFor(providerId)
            val loadedKey: Result<String?> = if (activeProvider.requiresApiKey) runCatching { vault.loadApiKey(providerId) } else Result.success(null)
            apiKey = loadedKey.getOrNull()
            val positions = runCatching { store.positions() }.getOrDefault(emptyList())
            val history = runCatching { store.history() }.getOrDefault(emptyList())
            val startupMessage = when {
                legacyKeyCleared && activeProvider.requiresApiKey -> "The old shared key was cleared because provider keys are not interchangeable. API Ninjas gold data is futures, not XAU/USD spot; use a Twelve Data key for candle history."
                legacyKeyCleared -> "The old shared key was cleared because provider keys are not interchangeable. Gold API needs no key for its current XAU/USD quote."
                loadedKey.isFailure -> loadedKey.exceptionOrNull()?.message ?: "Stored provider key could not be read."
                activeProvider.requiresApiKey && apiKey.isNullOrBlank() -> "Add a ${providerId.displayName} key in Settings. No market data is simulated."
                !activeProvider.requiresApiKey -> "No key required. Test the live XAU/USD quote; historical candles are not included."
                else -> ""
            }
            _state.update { it.copy(
                selectedProvider = providerId,
                apiKeyConfigured = !activeProvider.requiresApiKey || !apiKey.isNullOrBlank(),
                positions = positions,
                history = history,
                startingBalance = prefs.startingBalance,
                riskPercent = prefs.riskPercent,
                spread = prefs.spread,
                slippage = prefs.slippage,
                minimumScore = prefs.minimumScore,
                minimumRiskReward = prefs.minimumRiskReward,
                autoPaperTrading = prefs.autoPaperTrading,
                realizedPnl = history.sumOf { trade -> trade.netPnl },
                feed = FeedStatus(provider = providerId.displayName, message = startupMessage.ifBlank { "Provider key loaded; test the connection to refresh real data." }),
                statusMessage = startupMessage,
            ) }
        }
    }

    fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        monitoringJob = viewModelScope.launch {
            var backoff = 60_000L
            while (true) {
                val selected = _state.value.selectedProvider
                val activeProvider = providerFor(selected)
                if (activeProvider.requiresApiKey && apiKey.isNullOrBlank()) {
                    _state.update { current ->
                        if (current.selectedProvider != selected) current else current.copy(
                            feed = FeedStatus(FeedState.NOT_CONFIGURED, activeProvider.name, message = "${activeProvider.name} API key required; no market data is being shown."),
                        )
                    }
                    delay(60_000L)
                } else {
                    val success = refreshOnce()
                    backoff = if (success) 60_000L else (backoff * 2).coerceAtMost(30 * 60_000L)
                    delay(if (success) 300_000L else backoff)
                }
            }
        }
    }

    fun stopMonitoring() { monitoringJob?.cancel(); monitoringJob = null }

    fun selectProvider(providerId: MarketProviderId) {
        if (_state.value.selectedProvider == providerId) return
        viewModelScope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                val activeProvider = providerFor(providerId)
                val loaded: Result<String?> = if (activeProvider.requiresApiKey) runCatching { vault.loadApiKey(providerId) } else Result.success(null)
                apiKey = loaded.getOrNull()
                prefs.marketProvider = providerId
                val message = loaded.exceptionOrNull()?.message
                    ?: if (activeProvider.requiresApiKey) "Selected ${providerId.displayName}. Its API key is stored separately from other providers."
                    else "Selected ${providerId.displayName}. No key is sent; this source provides a current XAU/USD quote only."
                _state.update { current ->
                    current.copy(
                        selectedProvider = providerId,
                        feed = FeedStatus(FeedState.NOT_CONFIGURED, providerId.displayName,
                            message = if (activeProvider.requiresApiKey && apiKey.isNullOrBlank()) "Add a ${providerId.displayName} key and test the connection." else if (activeProvider.requiresApiKey) "${providerId.displayName} key loaded; test the connection to refresh." else "No key required. Test to fetch the current XAU/USD quote; signals are disabled without candles."),
                        quote = null,
                        candles = emptyList(),
                        signal = waitingSignal("provider-${providerId.preferenceKey}"),
                        openPnl = 0.0,
                        apiKeyConfigured = !activeProvider.requiresApiKey || !apiKey.isNullOrBlank(),
                        isConnecting = false,
                        statusMessage = message,
                    )
                }
            }
        }
    }

    fun saveApiKey(value: String) {
        val providerId = _state.value.selectedProvider
        val activeProvider = providerFor(providerId)
        if (!activeProvider.requiresApiKey) {
            _state.update { current -> if (current.selectedProvider != providerId) current else current.copy(statusMessage = "${activeProvider.name} does not require or accept an API key.") }
            return
        }
        val normalized = value.trim()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                refreshMutex.withLock {
                    if (normalized.isBlank()) vault.clearApiKey(providerId) else vault.saveApiKey(providerId, normalized)
                    if (_state.value.selectedProvider == providerId) {
                        apiKey = normalized.takeIf { it.isNotBlank() }
                        _state.update { current ->
                            if (current.selectedProvider != providerId) current else current.copy(
                                apiKeyConfigured = apiKey != null,
                                statusMessage = if (apiKey == null) "${providerId.displayName} key removed." else "${providerId.displayName} key stored encrypted on this device. Testing connection…",
                                feed = if (apiKey == null) FeedStatus(FeedState.NOT_CONFIGURED, providerId.displayName, message = "Provider key removed; no market data is being requested.") else current.feed,
                                quote = if (apiKey == null) null else current.quote,
                                candles = if (apiKey == null) emptyList() else current.candles,
                                signal = if (apiKey == null) waitingSignal("key-removed-${providerId.preferenceKey}") else current.signal,
                                openPnl = if (apiKey == null) 0.0 else current.openPnl,
                            )
                        }
                    }
                }
                if (normalized.isNotBlank() && _state.value.selectedProvider == providerId) testConnection()
            } catch (error: Exception) {
                _state.update { current ->
                    if (current.selectedProvider != providerId) current else current.copy(statusMessage = error.message ?: "Unable to store this provider key securely.")
                }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val providerId = _state.value.selectedProvider
            val activeProvider = providerFor(providerId)
            val key = apiKey
            if (activeProvider.requiresApiKey && key.isNullOrBlank()) {
                _state.update { current -> if (current.selectedProvider != providerId) current else current.copy(statusMessage = "Enter and save a ${activeProvider.name} key first.") }
                return@launch
            }
            _state.update { current ->
                if (current.selectedProvider != providerId) current else current.copy(
                    isConnecting = true,
                    feed = FeedStatus(FeedState.CONNECTING, activeProvider.name, message = "Testing ${activeProvider.name} connection…"),
                )
            }
            val result = runCatching { refreshMutex.withLock { activeProvider.fetchSnapshot(key.orEmpty()) } }
            if (_state.value.selectedProvider != providerId) return@launch
            result.onSuccess { snapshot ->
                val clean = MarketMath.cleanCandles(snapshot.candles)
                val quoteOnlyMessage = "Current XAU/USD quote received. This public source has no historical candles, so signals and new paper entries stay disabled."
                _state.update { current ->
                    if (current.selectedProvider != providerId) current else current.copy(
                        isConnecting = false,
                        feed = statusFor(snapshot.quote, activeProvider, if (activeProvider.suppliesHistoricalCandles) "Connection test passed; refreshed with real ${snapshot.quote.symbol} data." else quoteOnlyMessage),
                        quote = snapshot.quote,
                        candles = if (activeProvider.suppliesHistoricalCandles) clean else emptyList(),
                        signal = if (activeProvider.suppliesHistoricalCandles) current.signal else waitingSignal("quote-only-${providerId.preferenceKey}", quoteOnlyMessage),
                        openPnl = if (activeProvider.suppliesHistoricalCandles) current.openPnl else 0.0,
                        lastUpdatedAt = snapshot.quote.receivedAt,
                        statusMessage = if (activeProvider.suppliesHistoricalCandles) "Connection test passed. ${snapshot.quote.symbol} is provider-returned data, not a simulated feed." else quoteOnlyMessage,
                    )
                }
            }.onFailure { error ->
                val message = safeMessage(error)
                _state.update { current ->
                    if (current.selectedProvider != providerId) current else current.copy(
                        isConnecting = false,
                        feed = FeedStatus(FeedState.ERROR, activeProvider.name, message = message),
                        statusMessage = message,
                    )
                }
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
            }.onFailure { error -> _state.update { state -> state.copy(statusMessage = "Account reset failed: ${error.message}") } }
        }
    }

    private suspend fun refreshOnce(): Boolean = refreshMutex.withLock {
        val providerId = _state.value.selectedProvider
        val activeProvider = providerFor(providerId)
        val key = apiKey
        if (activeProvider.requiresApiKey && key.isNullOrBlank()) return false
        _state.update { current ->
            if (current.selectedProvider != providerId) current else current.copy(
                feed = FeedStatus(FeedState.CONNECTING, activeProvider.name, message = if (activeProvider.suppliesHistoricalCandles) "Requesting current ${providerId.instrumentLabel} 5-minute data…" else "Requesting the current XAU/USD quote…"),
            )
        }
        return try {
            val snapshot = activeProvider.fetchSnapshot(key.orEmpty())
            val candles = MarketMath.cleanCandles(snapshot.candles)
            if (!activeProvider.suppliesHistoricalCandles) {
                val now = System.currentTimeMillis() / 1000L
                val message = "Current XAU/USD quote only. Historical candles are not available from this no-key endpoint; signals and new paper entries are disabled."
                val status = statusFor(snapshot.quote, activeProvider, message)
                _state.update { current ->
                    if (current.selectedProvider != providerId) current else current.copy(
                        feed = status,
                        quote = snapshot.quote,
                        candles = emptyList(),
                        signal = waitingSignal("quote-only-${providerId.preferenceKey}-${snapshot.quote.marketTimestamp}", message),
                        openPnl = 0.0,
                        lastUpdatedAt = snapshot.quote.receivedAt,
                        statusMessage = if (status.state == FeedState.STALE) status.message else message,
                    )
                }
                return true
            }
            if (candles.size < 2) throw MarketDataException("${activeProvider.name} returned too few valid candles.")
            val now = System.currentTimeMillis() / 1000L
            val signal = SignalEngine(_state.value.minimumScore, _state.value.minimumRiskReward).evaluate(candles, now)
            var positions = store.positions()
            var history = store.history()
            val marketBar = candles.last()
            val closing = positions.filter { it.provider == activeProvider.name }.mapNotNull { position ->
                // Never use one provider/instrument's bars to close a position opened on another.
                if (marketBar.timestamp <= position.marketDataAt || now - snapshot.quote.marketTimestamp > 900L) return@mapNotNull null
                val reason = PaperTradingEngine.exitReason(position, marketBar) ?: return@mapNotNull null
                val rawExit = when {
                    reason == "Stop loss" && position.direction == Direction.BUY -> minOf(position.stopLoss, marketBar.open)
                    reason == "Stop loss" -> maxOf(position.stopLoss, marketBar.open)
                    reason == "Take profit" -> position.takeProfit
                    else -> snapshot.quote.last
                }
                PaperTradingEngine.close(position, rawExit, now, reason)
            }
            closing.forEach { store.close(it) }
            if (closing.isNotEmpty()) { positions = store.positions(); history = store.history() }
            val age = now - snapshot.quote.marketTimestamp
            val status = if (age > 900L) FeedStatus(FeedState.STALE, activeProvider.name, snapshot.quote.receivedAt, snapshot.quote.latencyMs,
                "STALE DATA: latest provider candle is ${age / 60} min old.")
            else statusFor(snapshot.quote, activeProvider, "Real ${snapshot.quote.symbol} data received; 5-minute candles only.")
            val equity = _state.value.startingBalance + history.sumOf { it.netPnl }
            if (status.state == FeedState.LIVE && _state.value.autoPaperTrading && signal.direction != Direction.WAIT &&
                signal.score >= _state.value.minimumScore && positions.isEmpty() && prefs.lastHandledSignal != signal.fingerprint &&
                canOpenTrade(history, equity, now)) {
                val entry = PaperTradingEngine.entryFill(signal.direction, snapshot.quote.last, _state.value.spread, _state.value.slippage)
                val risk = abs((signal.entry ?: entry) - (signal.stopLoss ?: entry))
                val quantity = if (risk > 0) PaperTradingEngine.quantityOz(equity, _state.value.riskPercent, entry,
                    if (signal.direction == Direction.BUY) entry - risk else entry + risk) else 0.0
                if (quantity > 0 && signal.stopLoss != null && signal.takeProfit != null) {
                    val offsetSL = abs(signal.entry!! - signal.stopLoss)
                    val offsetTP = abs(signal.takeProfit - signal.entry)
                    val position = PaperPosition(UUID.randomUUID().toString(), signal.direction, entry,
                        if (signal.direction == Direction.BUY) entry - offsetSL else entry + offsetSL,
                        if (signal.direction == Direction.BUY) entry + offsetTP else entry - offsetTP,
                        quantity, now, snapshot.quote.marketTimestamp, signal.score, regime = signal.regime,
                        provider = activeProvider.name, signalId = signal.fingerprint, spreadAssumption = _state.value.spread,
                        slippageAssumption = _state.value.slippage, entryMarketPrice = snapshot.quote.last)
                    runCatching { store.insert(position) }.onSuccess { prefs.lastHandledSignal = signal.fingerprint; positions = store.positions() }
                }
            }
            val realized = history.sumOf { it.netPnl }
            val openPnl = positions.filter { it.provider == activeProvider.name }.sumOf { position -> unrealized(position, snapshot.quote.last) }
            _state.update { current ->
                if (current.selectedProvider != providerId) current else current.copy(
                    feed = status,
                    quote = snapshot.quote,
                    candles = candles,
                    signal = signal,
                    positions = positions,
                    history = history,
                    realizedPnl = realized,
                    openPnl = openPnl,
                    lastUpdatedAt = snapshot.quote.receivedAt,
                    statusMessage = if (status.state == FeedState.STALE) status.message else "",
                )
            }
            true
        } catch (error: Exception) {
            val message = safeMessage(error)
            _state.update { current ->
                if (current.selectedProvider != providerId) current else current.copy(
                    feed = FeedStatus(FeedState.ERROR, activeProvider.name, current.lastUpdatedAt, message = message),
                    statusMessage = message,
                )
            }
            false
        }
    }

    private fun providerFor(id: MarketProviderId): MarketDataProvider = providers.getValue(id)

    private fun canOpenTrade(history: List<TradeRecord>, equity: Double, now: Long): Boolean {
        val dayStart = now - 86_400L
        val dailyNet = history.filter { it.closedAt >= dayStart }.sumOf { it.netPnl }
        if (dailyNet <= -equity * 0.02) return false
        val lastLoss = history.filter { it.netPnl < 0 }.maxOfOrNull { it.closedAt }
        return lastLoss == null || now - lastLoss >= 30 * 60L
    }

    private fun unrealized(position: PaperPosition, last: Double): Double {
        val exit = PaperTradingEngine.exitFill(position.direction, last, position.spreadAssumption, position.slippageAssumption)
        return if (position.direction == Direction.BUY) (exit - position.entryPrice) * position.quantityOz else (position.entryPrice - exit) * position.quantityOz
    }

    private fun statusFor(quote: MarketQuote, provider: MarketDataProvider, message: String): FeedStatus {
        val age = System.currentTimeMillis() / 1000L - quote.marketTimestamp
        val fresh = age <= 900L
        return FeedStatus(if (fresh) FeedState.LIVE else FeedState.STALE, provider.name, quote.receivedAt, quote.latencyMs,
            if (fresh) message else "STALE DATA: latest provider ${if (provider.suppliesHistoricalCandles) "candle" else "quote"} is ${age.coerceAtLeast(0) / 60} min old.")
    }

    private fun waitingSignal(reason: String, explanation: String = "Connect a market-data provider to calculate signals.") = Signal(
        Direction.WAIT, 0, listOf(explanation), "Insufficient Data",
        timestamp = System.currentTimeMillis() / 1000L, fingerprint = reason,
    )

    private fun safeMessage(error: Throwable): String = when (error) {
        is MarketDataException -> error.message ?: "Provider request failed."
        else -> error.message?.take(200) ?: "Provider request failed. Check network and API plan."
    }

    override fun onCleared() { stopMonitoring(); store.close(); super.onCleared() }
}
