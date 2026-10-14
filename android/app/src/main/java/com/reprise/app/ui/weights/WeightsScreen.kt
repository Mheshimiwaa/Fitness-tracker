package com.reprise.app.ui.weights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reprise.app.R
import com.reprise.app.components.DetailScreen
import com.reprise.app.components.ErrorBanner
import com.reprise.app.components.RepRiseCard
import com.reprise.app.components.ScreenContainer
import com.reprise.app.components.WeightChart
import com.reprise.app.domain.model.WeightChange
import com.reprise.app.domain.model.WeightEntry
import com.reprise.app.domain.model.WeightUnit
import com.reprise.app.ui.theme.RepRiseTheme
import com.reprise.app.util.formatShortDate
import com.reprise.app.util.formatSignedWeight
import com.reprise.app.util.formatWeight

/**
 * The body-weight screen: a summary, a trend line, and the full history with
 * add / edit / delete.
 *
 * All the data work lives in [WeightsViewModel]; this file is purely how the
 * screen looks.
 */
@Composable
fun WeightsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeightsViewModel = viewModel(factory = WeightsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Which entry the delete-confirmation dialog is about (null = closed).
    var pendingDelete by remember { mutableStateOf<WeightEntry?>(null) }

    DetailScreen(
        title = stringResource(R.string.title_weight),
        onBack = onBack,
        modifier = modifier,
    ) {
        PeriodSelector(selected = state.selectedDays, onSelect = viewModel::selectDays)

        state.errorMessage?.takeIf { state.editor == null }?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.action_retry),
                onAction = viewModel::refresh,
            )
        }

        Button(
            onClick = viewModel::startAdd,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.weights_log_button))
        }

        when {
            state.isLoading && state.entries.isEmpty() -> CenteredLoader()

            state.entries.isEmpty() -> EmptyWeightCard()

            else -> {
                SummaryCard(state = state)
                ChartCard(state = state)

                Text(
                    text = stringResource(R.string.weights_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                state.entries.forEach { entry ->
                    HistoryCard(
                        entry = entry,
                        onEdit = { viewModel.startEdit(entry) },
                        onDelete = { pendingDelete = entry },
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        WeightEditorDialog(
            editor = editor,
            unitLabel = stringResource(state.preferredUnit.shortLabelRes),
            onWeightChange = viewModel::onWeightChanged,
            onNoteChange = viewModel::onNoteChanged,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissEditor,
        )
    }

    pendingDelete?.let { entry ->
        DeleteEntryDialog(
            entry = entry,
            onConfirm = {
                viewModel.delete(entry.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Period selector
// ---------------------------------------------------------------------------

@Composable
private fun PeriodSelector(selected: Int?, onSelect: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PERIOD_OPTIONS.forEach { days ->
            FilterChip(
                selected = days == selected,
                onClick = { onSelect(days) },
                label = { Text(text = stringResource(periodLabelRes(days))) },
            )
        }
    }
}

private fun periodLabelRes(days: Int?): Int = when (days) {
    7 -> R.string.weights_period_7
    30 -> R.string.weights_period_30
    90 -> R.string.weights_period_90
    else -> R.string.weights_period_all
}

// ---------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------

@Composable
private fun SummaryCard(state: WeightsUiState) {
    val unitLabel = stringResource(state.preferredUnit.shortLabelRes)
    val latest = state.entries.firstOrNull()
    val change = state.change

    RepRiseCard {
        // A label that explains which window these numbers cover.
        Text(
            text = state.selectedDays
                ?.let { stringResource(R.string.home_weight_window, it) }
                ?: stringResource(R.string.weights_period_all),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatColumn(
                label = stringResource(R.string.weights_summary_latest),
                value = latest?.let { "${formatWeight(it.weight)} $unitLabel" },
            )
            StatColumn(
                label = stringResource(R.string.weights_summary_change),
                value = change?.let { "${formatSignedWeight(it.amount)} $unitLabel" },
            )
            StatColumn(
                label = stringResource(R.string.weights_summary_entries),
                value = state.entries.size.toString(),
            )
        }
    }
}

@Composable
private fun RowScope.StatColumn(
    label: String,
    value: String?,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value ?: stringResource(R.string.weights_summary_none),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------
// Chart
// ---------------------------------------------------------------------------

@Composable
private fun ChartCard(state: WeightsUiState) {
    val entries = state.chronologicalEntries
    val unitLabel = stringResource(state.preferredUnit.shortLabelRes)

    RepRiseCard {
        Text(
            text = stringResource(R.string.weights_chart_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (entries.size < 2) {
            Text(
                text = stringResource(R.string.weights_chart_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val weights = entries.map { it.weight }
            WeightChart(
                entries = entries,
                highLabel = stringResource(
                    R.string.weights_chart_high,
                    "${formatWeight(weights.max())} $unitLabel",
                ),
                lowLabel = stringResource(
                    R.string.weights_chart_low,
                    "${formatWeight(weights.min())} $unitLabel",
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------

@Composable
private fun HistoryCard(
    entry: WeightEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    RepRiseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatWeight(entry.weight),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(entry.unit.shortLabelRes),
                        modifier = Modifier.padding(bottom = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatShortDate(entry.measuredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.action_edit),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        }
    }
}

@Composable
private fun EmptyWeightCard() {
    RepRiseCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_scale),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.weights_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.weights_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CenteredLoader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun WeightEditorDialog(
    editor: WeightEditorState,
    unitLabel: String,
    onWeightChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A server-side problem with the weight becomes the field's error text;
    // every other failure is shown as a banner.
    val weightErrorText = when (editor.weightError) {
        null -> null
        WeightValidation.REQUIRED -> stringResource(R.string.weights_error_weight_required)
        WeightValidation.NOT_A_NUMBER -> stringResource(R.string.weights_error_weight_invalid)
        WeightValidation.NOT_POSITIVE -> stringResource(R.string.weights_error_weight_positive)
        WeightValidation.SERVER -> editor.errorMessage
    }
    val bannerText = if (editor.weightError == null) editor.errorMessage else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (editor.isEditing) R.string.weights_edit_title else R.string.weights_add_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bannerText != null) {
                    ErrorBanner(message = bannerText)
                }
                OutlinedTextField(
                    value = editor.weightText,
                    onValueChange = onWeightChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.weights_field_weight, unitLabel)) },
                    singleLine = true,
                    enabled = !editor.isSaving,
                    isError = editor.weightError != null,
                    supportingText = weightErrorText?.let { text -> { Text(text) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = editor.noteText,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.weights_field_note)) },
                    placeholder = { Text(stringResource(R.string.weights_field_note_hint)) },
                    singleLine = true,
                    enabled = !editor.isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !editor.isSaving) {
                if (editor.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.action_save_short))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DeleteEntryDialog(
    entry: WeightEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weights_delete_title)) },
        text = {
            Text(
                stringResource(R.string.weights_delete_message) +
                    " (${formatWeight(entry.weight)} ${entry.unit.apiValue})"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private fun previewEntry(id: String, weight: Double, note: String? = null) = WeightEntry(
    id = id,
    weight = weight,
    unit = WeightUnit.KG,
    weightKg = weight,
    measuredAt = "2026-10-0${id}T08:00:00.000Z",
    note = note,
)

@Preview(showBackground = true)
@Composable
private fun WeightsHistoryPreview() {
    RepRiseTheme {
        ScreenContainer(title = "Weight") {
            SummaryCard(
                state = WeightsUiState(
                    isLoading = false,
                    entries = listOf(previewEntry("3", 82.4, "morning"), previewEntry("2", 83.1)),
                    change = WeightChange(amount = -1.4, unit = WeightUnit.KG),
                )
            )
            ChartCard(
                state = WeightsUiState(
                    isLoading = false,
                    entries = listOf(previewEntry("1", 84.0), previewEntry("2", 83.1), previewEntry("3", 82.4)),
                    change = WeightChange(amount = -1.6, unit = WeightUnit.KG),
                )
            )
            HistoryCard(entry = previewEntry("3", 82.4, "morning"), onEdit = {}, onDelete = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WeightsEmptyPreview() {
    RepRiseTheme {
        ScreenContainer(title = "Weight") {
            EmptyWeightCard()
        }
    }
}
