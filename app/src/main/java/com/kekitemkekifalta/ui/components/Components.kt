@file:OptIn(ExperimentalFoundationApi::class)

package com.kekitemkekifalta.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kekitemkekifalta.R
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.core.StockState
import com.kekitemkekifalta.core.StockStatus
import com.kekitemkekifalta.ui.theme.KekTheme

@Composable
fun KekIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = KekTheme.colors.ink,
    size: Dp = 22.dp,
) {
    Icon(painterResource(id), contentDescription, modifier.size(size), tint = tint)
}

/**
 * The app's signature surface: thick ink outline and a hard offset shadow, like a sticker.
 * Pressing it pushes the card into its shadow.
 */
@Composable
fun StickerCard(
    modifier: Modifier = Modifier,
    color: Color = KekTheme.colors.card,
    borderColor: Color = KekTheme.colors.outline,
    shape: Shape = RoundedCornerShape(18.dp),
    elevation: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateDpAsState(if (pressed && onClick != null) 1.dp else elevation, label = "lift")
    val shadow = KekTheme.colors.shadow
    val click = if (onClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Column(
        modifier
            .padding(end = elevation, bottom = elevation)
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                val o = lift.toPx()
                translate(o, o) { drawOutline(outline, shadow) }
            }
            .offset(x = elevation - lift, y = elevation - lift)
            .clip(shape)
            .background(color)
            .border(2.dp, borderColor, shape)
            .then(click)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun KekButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    color: Color = KekTheme.colors.tomato,
    contentColor: Color = KekTheme.colors.onAccent,
    big: Boolean = false,
    enabled: Boolean = true,
) {
    StickerCard(
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
        color = color,
        shape = CircleShape,
        elevation = if (big) 4.dp else 3.dp,
        contentPadding = if (big) PaddingValues(horizontal = 22.dp, vertical = 15.dp) else PaddingValues(horizontal = 16.dp, vertical = 9.dp),
        onClick = if (enabled) onClick else null,
    ) {
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                KekIcon(icon, null, tint = contentColor, size = if (big) 22.dp else 18.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Secondary button: card colored with ink text. */
@Composable
fun KekGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    big: Boolean = false,
    enabled: Boolean = true,
) = KekButton(text, onClick, modifier, icon, KekTheme.colors.card, KekTheme.colors.ink, big, enabled)

@Composable
fun IconCircleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = KekTheme.colors.card,
    tint: Color = KekTheme.colors.ink,
) {
    StickerCard(
        modifier = modifier,
        color = color,
        shape = CircleShape,
        elevation = 2.dp,
        contentPadding = PaddingValues(10.dp),
        onClick = onClick,
    ) {
        KekIcon(icon, contentDescription, tint = tint, size = 20.dp)
    }
}

@Composable
fun Pill(
    text: String,
    background: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = KekTheme.colors.ink,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .border(1.5.dp, KekTheme.colors.outline, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun statusColors(status: StockStatus): Pair<Color, Color> {
    val c = KekTheme.colors
    return when (status) {
        StockStatus.HAVE -> c.leaf to c.leafSoft
        StockStatus.RUNNING_LOW -> c.mustard to c.mustardSoft
        StockStatus.PROBABLY_OUT -> c.tomato to c.tomatoSoft
    }
}

@Composable
fun StatusPill(status: StockStatus, modifier: Modifier = Modifier) {
    Pill(status.label, statusColors(status).second, modifier)
}

/** How much is left, as a fuel gauge. */
@Composable
fun FuelBar(state: StockState, modifier: Modifier = Modifier) {
    val left = (1.0 - state.progress).coerceIn(0.0, 1.0).toFloat()
    val (strong, _) = statusColors(state.status)
    Box(
        modifier
            .height(10.dp)
            .clip(CircleShape)
            .background(KekTheme.colors.paper)
            .border(1.5.dp, KekTheme.colors.outline, CircleShape),
    ) {
        if (left > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(left.coerceAtLeast(0.06f))
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(strong),
            )
        }
    }
}

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
) {
    val c = KekTheme.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(if (selected) c.ink else c.card)
            .border(1.5.dp, c.outline, CircleShape)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(leading, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) c.paper else c.ink, maxLines = 1)
    }
}

@Composable
fun SectorChip(sector: Sector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    ChoiceChip(sector.label, selected, onClick, modifier, leading = sector.emoji)

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconCircleButton(R.drawable.ic_back, "Voltar", onBack)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack == null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                color = KekTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = KekTheme.colors.muted, maxLines = 2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, count: Int? = null, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = KekTheme.colors.ink)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Pill("$count", KekTheme.colors.mustardSoft)
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun EmptyState(emoji: String, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = KekTheme.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = KekTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}
