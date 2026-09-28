package com.rollinkxx.xauusd

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun dashboardLaunchesWithoutLoginAndSettingsAreReachable() {
        composeRule.onNodeWithText("XAUUSD SIGNAL LAB").assertIsDisplayed()
        composeRule.onNodeWithText("Configure market data").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Market data provider").assertIsDisplayed()
        composeRule.onNodeWithText("Paper trading assumptions").assertIsDisplayed()
    }
}
