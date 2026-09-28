package com.rollinkxx.xauusd.data.local

import android.content.Context
import com.rollinkxx.xauusd.domain.model.MarketProviderId

class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    var marketProvider: MarketProviderId
        get() = MarketProviderId.fromPreference(prefs.getString("market_provider", null))
        set(value) { prefs.edit().putString("market_provider", value.preferenceKey).apply() }

    var startingBalance: Double
        get() = prefs.getFloat("starting_balance", 10_000.0f).toDouble()
        set(value) { prefs.edit().putFloat("starting_balance", value.toFloat()).apply() }
    var riskPercent: Double
        get() = prefs.getFloat("risk_percent", 1.0f).toDouble()
        set(value) { prefs.edit().putFloat("risk_percent", value.toFloat()).apply() }
    var spread: Double
        get() = prefs.getFloat("spread_assumption", 0.30f).toDouble()
        set(value) { prefs.edit().putFloat("spread_assumption", value.toFloat()).apply() }
    var slippage: Double
        get() = prefs.getFloat("slippage_assumption", 0.05f).toDouble()
        set(value) { prefs.edit().putFloat("slippage_assumption", value.toFloat()).apply() }
    var minimumScore: Int
        get() = prefs.getInt("minimum_score", 70)
        set(value) { prefs.edit().putInt("minimum_score", value).apply() }
    var minimumRiskReward: Double
        get() = prefs.getFloat("minimum_rr", 1.5f).toDouble()
        set(value) { prefs.edit().putFloat("minimum_rr", value.toFloat()).apply() }
    var autoPaperTrading: Boolean
        get() = prefs.getBoolean("auto_paper", true)
        set(value) { prefs.edit().putBoolean("auto_paper", value).apply() }
    var lastHandledSignal: String?
        get() = prefs.getString("last_handled_signal", null)
        set(value) { prefs.edit().putString("last_handled_signal", value).apply() }
}
