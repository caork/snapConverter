package com.snapconverter.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.snapconverter.app.ui.theme.ios27.Ios27Motion

/**
 * Motion on the iOS 27 spring scale (animations.json). iOS gives press feedback
 * by filling or dimming the target, not by scaling it, so [pressable] carries no
 * ripple and no scale — the caller decides what changes.
 */
object SnapAnimations {
    const val ContentFadeMs = Ios27Motion.ContentFadeMs
    const val ContentFadeOutMs = 150

    /** Success mark pop on the result group — bouncy. */
    val SuccessPop = spring<Float>(dampingRatio = 0.65f, stiffness = 380f)

    /** Inline panel expand — gentle. */
    val PanelEnter: EnterTransition = fadeIn(
        animationSpec = tween(ContentFadeMs, easing = Ios27Motion.AppleDefault),
    ) + expandVertically(
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
    )

    val PanelExit: ExitTransition = fadeOut(
        animationSpec = tween(ContentFadeOutMs, easing = Ios27Motion.AppleDefault),
    ) + shrinkVertically(
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 520f),
    )
}

/** Tap handling without Material indication; visual feedback is the caller's. */
@Composable
fun Modifier.pressable(
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}
