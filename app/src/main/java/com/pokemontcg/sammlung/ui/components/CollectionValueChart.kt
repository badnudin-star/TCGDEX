package com.pokemontcg.sammlung.ui.components

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import com.pokemontcg.sammlung.util.buildValueSeries

data class ValuePoint(
    val timestamp: Long,
    val totalValue: Double
)

@Composable
fun CollectionValueChart(
    history: List<PriceHistoryEntity>,
    quantities: Map<String, Int>,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var selectedDays by remember { mutableIntStateOf(30) }

    val points = remember(history, quantities, selectedDays) {
        buildValueSeries(history, quantities, selectedDays)
    }
    val relevantHistoryCount = remember(history, quantities) {
        history.count { it.cardId in quantities }
    }

    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.ui_collection_value_chart_description),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(7 to stringResource(R.string.ui_days_7), 30 to stringResource(R.string.ui_days_30), 90 to stringResource(R.string.ui_days_90), 0 to stringResource(R.string.ui_all_time)).forEach { (days, label) ->
                FilterChip(
                    selected = selectedDays == days,
                    onClick = { selectedDays = days },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        if (relevantHistoryCount < 2 || points.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.ui_chart_not_enough_data),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val primaryColor = MaterialTheme.colorScheme.primary
            val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

            val minValue = points.minOf { it.totalValue }.coerceAtLeast(0.0)
            val maxValue = points.maxOf { it.totalValue }.let { if (it <= minValue) minValue + 10.0 else it }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(vertical = 8.dp)
            ) {
                val width = size.width
                val height = size.height

                for (i in 0..3) {
                    val y = height * (i / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val stepX = width / (points.size - 1)
                val path = Path()

                points.forEachIndexed { index, point ->
                    val x = index * stepX
                    val normalizedY = ((point.totalValue - minValue) / (maxValue - minValue)).toFloat()
                    val y = height - (normalizedY * height)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }

                    drawCircle(color = primaryColor, radius = 3.dp.toPx(), center = Offset(x, y))
                }

                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
