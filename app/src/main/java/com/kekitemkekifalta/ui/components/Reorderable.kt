package com.kekitemkekifalta.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A small drag-to-reorder column (fine for a few dozen rows, e.g. market aisles).
 * Each row gets a [Modifier] to put on its drag handle. [onMove] fires while dragging;
 * [onDragEnd] when the finger lifts, which is the moment to persist the new order.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    row: @Composable (item: T, dragging: Boolean, handle: Modifier) -> Unit,
) {
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnStart by rememberUpdatedState(onDragStart)
    val currentOnEnd by rememberUpdatedState(onDragEnd)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.forEach { item ->
            val k = keyOf(item)
            key(k) {
                val dragging = draggingKey == k
                val handle = Modifier.pointerInput(k) {
                    detectDragGestures(
                        onDragStart = {
                            draggingKey = k
                            offset = 0f
                            currentOnStart()
                        },
                        onDragEnd = {
                            draggingKey = null
                            offset = 0f
                            currentOnEnd()
                        },
                        onDragCancel = {
                            draggingKey = null
                            offset = 0f
                            currentOnEnd()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            offset += amount.y
                            val list = currentItems
                            val index = list.indexOfFirst { keyOf(it) == k }
                            if (index < 0) return@detectDragGestures
                            if (offset > 0 && index < list.lastIndex) {
                                val step = (heights[keyOf(list[index + 1])] ?: 0) + spacingPx
                                if (offset > step / 2) {
                                    currentOnMove(index, index + 1)
                                    offset -= step
                                }
                            } else if (offset < 0 && index > 0) {
                                val step = (heights[keyOf(list[index - 1])] ?: 0) + spacingPx
                                if (-offset > step / 2) {
                                    currentOnMove(index, index - 1)
                                    offset += step
                                }
                            }
                        },
                    )
                }
                Box(
                    Modifier
                        .onSizeChanged { heights[k] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) offset else 0f
                            scaleX = if (dragging) 1.02f else 1f
                            scaleY = if (dragging) 1.02f else 1f
                        },
                ) {
                    row(item, dragging, handle)
                }
            }
        }
    }
}
