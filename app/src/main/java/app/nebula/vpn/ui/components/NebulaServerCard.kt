package app.nebula.vpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun NebulaServerCard(
    name: String,
    protocol: String,
    pingMs: Long?,
    isTesting: Boolean,
    isSelected: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    countryCode: String? = null,
    onPingClick: (() -> Unit)? = null,
    isInsecure: Boolean = false,
    hasTls: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography

    val effectiveCountryCode = countryCode ?: detectCountryCode(name)

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.borderActive
            isFocused -> colors.accent
            else -> colors.borderSubtle
        },
        label = "ServerCardBorder"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.surfaceElevated else colors.surface,
        label = "ServerCardBackground"
    )

    val pingColor = when {
        pingMs == null || pingMs <= 0 -> colors.textMuted
        pingMs < 100 -> colors.statusOk
        pingMs < 300 -> colors.statusWarning
        else -> colors.statusError
    }

    val pingText = when {
        isTesting -> "..."
        pingMs == null || pingMs <= 0 -> "–"
        pingMs >= 9999 -> "Timeout"
        else -> "${pingMs}ms"
    }

    val accessibilityDesc = "$name, $protocol server. " +
            (if (isSelected) "Active. " else "") +
            "Latency: $pingText."

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shapes.card)
            .background(backgroundColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shapes.card)
            .semantics {
                role = Role.RadioButton
                contentDescription = accessibilityDesc
            }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect()
            }
            .padding(horizontal = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: Flag/Icon, Name, Protocol badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Country flag or node avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.borderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getFlagEmoji(effectiveCountryCode),
                    style = typography.titleSmall
                )
            }

            Spacer(modifier = Modifier.width(spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Protocol badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = protocol.uppercase(),
                            style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.primary
                        )
                    }

                    if (isInsecure) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.statusWarning.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "NO CERT",
                                style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.statusWarning
                            )
                        }
                    } else if (!hasTls) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.statusError.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "NO TLS",
                                style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.statusError
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(spacing.xs))

        // Right side: Ping badge, Favorite, Menu
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Ping latency chip
            Box(
                modifier = Modifier
                    .clip(shapes.chip)
                    .background(pingColor.copy(alpha = 0.12f))
                    .border(1.dp, pingColor.copy(alpha = 0.25f), shapes.chip)
                    .clickable(enabled = onPingClick != null && !isTesting) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPingClick?.invoke()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.accent
                    )
                } else {
                    Text(
                        text = pingText,
                        style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = pingColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(spacing.xs))

            // Favorite button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFavoriteToggle()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) colors.statusWarning else colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Context menu
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Server options",
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Selected checkmark
            if (isSelected) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Active server",
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

fun detectCountryCode(name: String): String? {
    val lower = name.lowercase()
    return when {
        lower.contains("герман") || lower.contains("germany") || lower.contains(" de") || lower.contains("_de") || lower.contains(" frankfurt") -> "DE"
        lower.contains("нидерланд") || lower.contains("netherlands") || lower.contains(" nl") || lower.contains("_nl") || lower.contains("амстердам") || lower.contains("amsterdam") -> "NL"
        lower.contains("сша") || lower.contains("usa") || lower.contains("united states") || lower.contains(" us") || lower.contains("_us") -> "US"
        lower.contains("росси") || lower.contains("russia") || lower.contains(" ru") || lower.contains("_ru") || lower.contains("москва") -> "RU"
        lower.contains("финлянд") || lower.contains("finland") || lower.contains(" fi") || lower.contains("_fi") || lower.contains("helsinki") -> "FI"
        lower.contains("швеци") || lower.contains("sweden") || lower.contains(" se") || lower.contains("_se") || lower.contains("stockholm") -> "SE"
        lower.contains("латви") || lower.contains("latvia") || lower.contains(" lv") || lower.contains("_lv") || lower.contains("riga") -> "LV"
        lower.contains("турци") || lower.contains("turkey") || lower.contains(" tr") || lower.contains("_tr") || lower.contains("istanbul") -> "TR"
        lower.contains("казах") || lower.contains("kazakhstan") || lower.contains(" kz") || lower.contains("_kz") || lower.contains("almaty") -> "KZ"
        lower.contains("япон") || lower.contains("japan") || lower.contains(" jp") || lower.contains("_jp") || lower.contains("tokyo") -> "JP"
        lower.contains("сингапур") || lower.contains("singapore") || lower.contains(" sg") || lower.contains("_sg") -> "SG"
        lower.contains("франци") || lower.contains("france") || lower.contains(" fr") || lower.contains("_fr") || lower.contains("paris") -> "FR"
        lower.contains("британ") || lower.contains("uk") || lower.contains("gb") || lower.contains("лондон") || lower.contains("london") -> "GB"
        else -> null
    }
}

private fun getFlagEmoji(countryCode: String?): String {
    if (countryCode == null || countryCode.length != 2) return "🌐"
    val firstChar = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
    val secondChar = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
}

@Preview(name = "Server Card Normal", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewNebulaServerCardNormal() {
    NebulaTheme {
        NebulaServerCard(
            name = "US East Cloudflare Warp",
            protocol = "VLESS",
            pingMs = 45L,
            isTesting = false,
            isSelected = true,
            isFavorite = true,
            onSelect = {},
            onFavoriteToggle = {},
            onMenuClick = {},
            countryCode = "US"
        )
    }
}
