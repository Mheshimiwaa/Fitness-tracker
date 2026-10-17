package com.reprise.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A consistent page frame used by every screen.
 *
 * It provides:
 *   - the standard 16dp horizontal screen padding,
 *   - a large title at the top,
 *   - vertical scrolling so content never gets clipped on small phones,
 *   - a consistent 16dp gap between children,
 *   - and a scrolling area that stops above the soft keyboard.
 *
 * Reusing one container keeps spacing identical across screens, which is a big
 * part of looking "polished".
 */
@Composable
fun ScreenContainer(
    title: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseModifier = modifier
        .fillMaxSize()
        // The app draws edge-to-edge, so Android does NOT resize the window when
        // the keyboard opens — the app is handed the keyboard's height as an
        // inset instead. Subtracting it here shortens the scrollable area, which
        // is what lets a focused text field scroll up above the keyboard rather
        // than sitting behind it.
        .imePadding()
        .padding(contentPadding)
    val scrollModifier =
        if (scrollable) baseModifier.verticalScroll(rememberScrollState()) else baseModifier

    Column(
        modifier = scrollModifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        content()
    }
}
