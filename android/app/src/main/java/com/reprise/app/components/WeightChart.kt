package com.reprise.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.reprise.app.domain.model.WeightEntry
import kotlin.math.max

/**
 * A minimal line chart of body weight over time.
 *
 * Why hand-draw it rather than add a charting library? A library would be a
 * large dependency for one small line, and drawing it with Compose's [Canvas]
 * keeps full control of the RepRise look (accent-coloured line, subtle grid).
 *
 * @param entries readings in CHRONOLOGICAL order (oldest first). The caller is
 *   responsible for reversing the API's newest-first list.
 * @param valueLabel formats each axis label, e.g. `{ formatWeight(it) + " kg" }`.
 */
@Composable
fun WeightChart(
    entries: List<WeightEntry>,
    highLabel: String,
    lowLabel: String,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outline,
) {
    // A single point is not a line — the caller shows a hint instead.
    if (entries.size < 2) return

    val weights = entries.map { it.weight }
    val lowest = weights.min()
    val highest = weights.max()
    // Guard against a flat line (all readings identical), which would divide by zero.
    val spread = max(highest - lowest, 0.0001)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ChartLabel(highLabel)
            ChartLabel(lowLabel)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val verticalPadding = 12.dp.toPx()

                // Leaves room for the dots' radius at both ends.
                val usableHeight = max(size.height - verticalPadding * 2, 1f)
                val lastIndex = (entries.size - 1).toFloat()

                // A faint baseline anchors the eye at the bottom edge.
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )

                val points = entries.mapIndexed { index, entry ->
                    val x = size.width * index / lastIndex
                    // Heavier readings sit higher, so the ratio is inverted.
                    val ratio = ((entry.weight - lowest) / spread).toFloat()
                    val y = verticalPadding + (1f - ratio) * usableHeight
                    Offset(x, y)
                }

                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (index in 1 until points.size) {
                        lineTo(points[index].x, points[index].y)
                    }
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )

                points.forEachIndexed { index, point ->
                    // The newest reading gets a filled marker so it stands out.
                    val isLatest = index == points.lastIndex
                    drawCircle(
                        color = lineColor,
                        radius = (if (isLatest) 5.dp else 3.dp).toPx(),
                        center = point,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
