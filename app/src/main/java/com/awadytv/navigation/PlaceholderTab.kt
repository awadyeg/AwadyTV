package com.awadytv.navigation

import androidx.compose.runtime.Composable
import com.awadytv.core.designsystem.EmptyView

@Composable
fun PlaceholderTab(tab: MainTab) {
    EmptyView(title = tab.name, message = "Coming soon")
}
