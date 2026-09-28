package com.rollinkxx.xauusd.domain.model

import org.junit.Assert.*
import org.junit.Test

class MarketProviderSelectionTest {
    @Test fun firstRunSelectsNoKeyXauUsdQuoteProvider() {
        val provider = MarketProviderId.fromPreference(null)
        assertEquals(MarketProviderId.GOLD_API_LIVE, provider)
        assertEquals("Spot XAU/USD", provider.instrumentLabel)
        assertFalse(provider.requiresApiKey)
        assertFalse(provider.suppliesHistoricalCandles)
    }

    @Test fun twelveDataRemainsTheCandleCapableProvider() {
        val provider = MarketProviderId.fromPreference("twelve_data")
        assertEquals(MarketProviderId.TWELVE_DATA, provider)
        assertTrue(provider.requiresApiKey)
        assertTrue(provider.suppliesHistoricalCandles)
    }

    @Test fun unknownStoredProviderFallsBackToExplicitKeyedSource() {
        assertEquals(MarketProviderId.TWELVE_DATA, MarketProviderId.fromPreference("unknown"))
    }
}
