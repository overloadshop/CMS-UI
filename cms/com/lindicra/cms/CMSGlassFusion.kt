package com.lindicra.cms

import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.UUID

data class CMSGlassFusionSettings(
    val groupId: String?,
    val mergeGap: Dp = 3.dp,
    val proximityOnly: Boolean = false,
    val progress: Float = 1f,
    val gravityStrength: Float = 1f
)

internal val LocalCMSGlassFusion = staticCompositionLocalOf<CMSGlassFusionSettings?> { null }

/** 每个实例有独立组号；嵌套覆盖，离开组的玻璃自动解除注册。 */
@Composable
fun CMSGlassFusionGroup(
    enabled: Boolean = true,
    mergeGap: Dp = 3.dp,
    proximityOnly: Boolean = false,
    progress: Float = 1f,
    gravityStrength: Float = 1f,
    content: @Composable () -> Unit
) {
    val id = remember { "cms-fusion-${UUID.randomUUID()}" }
    val settings = CMSGlassFusionSettings(
        if (enabled) id else null,
        mergeGap.coerceAtLeast(0.dp),
        proximityOnly,
        (progress.takeIf { it.isFinite() } ?: 1f).coerceIn(0f, 1f),
        (gravityStrength.takeIf { it.isFinite() } ?: 1f).coerceIn(0f, 4f)
    )
    CompositionLocalProvider(LocalCMSGlassFusion provides settings, content = content)
}
