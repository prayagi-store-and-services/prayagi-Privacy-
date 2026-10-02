package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallStateEnum
import com.example.data.model.ProtectionStatus
import com.example.data.model.ScreenStateEnum
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.NeonCyan

@Composable
fun PulsingShieldWidget(
    status: ProtectionStatus,
    isMicLocked: Boolean,
    isCamBlocked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == ProtectionStatus.PROTECTED) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val (statusColor, statusBg, statusTitle, statusSubtitle) = when (status) {
        ProtectionStatus.PROTECTED -> Quadruple(
            EmeraldSafe,
            Color(0x1A00E676),
            "SHIELD ACTIVE",
            if (isMicLocked) "Microphone Locked (Screen Off)" else "Hardware Shield Active"
        )
        ProtectionStatus.MONITORING_IDLE -> Quadruple(
            NeonCyan,
            Color(0x1A00E5FF),
            "MONITORING ACTIVE",
            "Screen On • Policy Ready to Arm"
        )
        ProtectionStatus.EXCEPTION_CALL -> Quadruple(
            AmberWarning,
            Color(0x1AFFB300),
            "CALL EXCEPTION",
            "Microphone Active for Voice Call"
        )
        ProtectionStatus.PARTIALLY_PROTECTED -> Quadruple(
            AmberWarning,
            Color(0x1AFFB300),
            "PARTIAL SHIELD",
            "Missing Microphone or Admin Policy"
        )
        ProtectionStatus.DISABLED -> Quadruple(
            CrimsonAlert,
            Color(0x1AFF5252),
            "GUARD DISABLED",
            "Tap to Activate Sensor Protection"
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .testTag("shield_pulsing_widget"),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(if (status == ProtectionStatus.PROTECTED || status == ProtectionStatus.MONITORING_IDLE) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(statusBg)
                    .border(2.dp, statusColor.copy(alpha = 0.6f), CircleShape)
            )

            // Inner core
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                statusColor.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
                    .border(2.dp, statusColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (status) {
                            ProtectionStatus.PROTECTED -> Icons.Default.Lock
                            ProtectionStatus.EXCEPTION_CALL -> Icons.Default.PhoneInTalk
                            ProtectionStatus.DISABLED -> Icons.Default.Warning
                            else -> Icons.Default.Shield
                        },
                        contentDescription = "Shield Icon",
                        tint = statusColor,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (status) {
                            ProtectionStatus.PROTECTED -> "ARMED"
                            ProtectionStatus.MONITORING_IDLE -> "MONITOR"
                            ProtectionStatus.EXCEPTION_CALL -> "CALL"
                            ProtectionStatus.DISABLED -> "OFF"
                            else -> "PARTIAL"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        ),
                        color = statusColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = statusTitle,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = statusColor
        )

        Text(
            text = statusSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SensorTelemetryCard(
    icon: ImageVector,
    title: String,
    stateText: String,
    isActive: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) accentColor.copy(alpha = 0.5f) else CyberBorder.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isActive) accentColor.copy(alpha = 0.15f) else Color(0x1A888888)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isActive) accentColor else Color(0xFF6B7280))
            )
        }
    }
}

@Composable
fun TransparencyNoticeCard(
    modifier: Modifier = Modifier,
    onLearnMoreClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("transparency_card"),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1500E5FF)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Transparency",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Android Security Transparency",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = NeonCyan
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Android security deliberately prevents third-party apps from silently altering other apps' permissions. SensorGuard uses Android-supported maximum enforcement: exclusive audio hardware guards on screen-off, call exception intelligence, and Device Admin camera policies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
