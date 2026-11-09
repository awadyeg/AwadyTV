package com.awadytv.navigation

import androidx.compose.runtime.Composable

@Composable
fun TabContent(tab: MainTab, navigator: AppNavigator) {
    when (tab) {
        else -> PlaceholderTab(tab)
    }
}
