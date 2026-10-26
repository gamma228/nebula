package app.nebula.vpn.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun OnboardingScreen(
    onComplete: (routingPresetDirectRu: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(0) }
    var selectedDirectRu by remember { mutableStateOf(true) }

    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Icon
        Box(
            modifier = Modifier
                .padding(top = spacing.xl)
                .size(80.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.15f))
                .border(1.dp, colors.primary.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(44.dp)
            )
        }

        // Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.onboarding_welcome),
                style = typography.displayLarge.copy(fontSize = 28.sp),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = stringResource(R.string.onboarding_welcome_desc),
                style = typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = spacing.md)
            )

            Spacer(modifier = Modifier.height(spacing.lg))

            Text(
                text = stringResource(R.string.onboarding_routing_preset),
                style = typography.titleMedium,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(spacing.sm))

            // Strategy Cards
            StrategyOptionCard(
                title = stringResource(R.string.onboarding_preset_russia_direct),
                subtitle = stringResource(R.string.preset_russia_direct_desc),
                isSelected = selectedDirectRu,
                onClick = { selectedDirectRu = true }
            )

            Spacer(modifier = Modifier.height(spacing.sm))

            StrategyOptionCard(
                title = stringResource(R.string.onboarding_preset_global_proxy),
                subtitle = stringResource(R.string.app_mode_global_desc),
                isSelected = !selectedDirectRu,
                onClick = { selectedDirectRu = false }
            )
        }

        // Bottom Actions
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { onComplete(selectedDirectRu) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = shapes.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_get_started),
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(spacing.xs))

            TextButton(
                onClick = { onComplete(false) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    style = typography.bodyMedium,
                    color = colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun StrategyOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                colors.primary.copy(alpha = 0.15f)
            } else {
                colors.surface
            }
        ),
        shape = shapes.card,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.primary else colors.borderSubtle,
                shape = shapes.card
            )
            .clickable { onClick() }
            .defaultMinSize(minHeight = 48.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}
