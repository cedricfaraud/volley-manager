package com.volley.manager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsMainNavigation() {
        composeRule.onNodeWithText("Volley Manager").assertIsDisplayed()
        composeRule.onNodeWithText("Calendrier").assertIsDisplayed()
    }
}
