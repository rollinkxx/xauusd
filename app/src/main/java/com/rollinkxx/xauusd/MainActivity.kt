package com.rollinkxx.xauusd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.rollinkxx.xauusd.presentation.MarketViewModel
import com.rollinkxx.xauusd.presentation.XauApp

class MainActivity : ComponentActivity() {
    private val model: MarketViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 16, 21)
        window.navigationBarColor = android.graphics.Color.rgb(11, 16, 21)
        setContent { XauApp(model) }
    }
    override fun onStart() { super.onStart(); model.startMonitoring() }
    override fun onStop() { model.stopMonitoring(); super.onStop() }
}
