package com.snapconverter.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.snapconverter.app.ui.theme.ios27.Ios27Motion

/**
 * Motion tokens on the iOS 27 spring scale (response/damping presets from
 * animations.json): snappy 0.3/0.8, bouncy 0.5/0.65, gentle 0.55/0.825,
 * stiff 0.25/1.0. Press feedback keeps the light end-only overshoot iOS uses.
 */
object SnapAnimations {
    // Panel expand / collapse — gentle spring, sheet-like.
    const val PanelEnterStiffness = 380f
    const val PanelExitStiffness = 520f

    // Content crossfade between stages.
    const val ContentFadeDuration = Ios27Motion.ContentFadeMs
    const val ContentFadeOutDuration = 150

    // Chevron rotation on collapsible headers — snappy.
    val ChevronRotationSpring = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 700f,
    )

    // Dialog / sheet scale-in.
    const val DialogScaleFrom = 0.94f
    const val DialogScaleDuration = Ios27Motion.AlertPresentMs

    // Success icon pop on the result card — bouncy.
    val SuccessPopSpring = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 380f,
    )

    // Press feedback: light scale dip, end-only rebound.
    const val PressedScale = 0.96f
    val PressSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 700f)

    /** Standard enter transition for inline collapsible panels. */
    val PanelEnter: EnterTransition = fadeIn(
        animationSpec = tween(ContentFadeDuration, easing = Ios27Motion.AppleDefaultEasing),
    ) + expandVertically(
        animationSpec = spring(dampingRatio = 0.85f, stiffness = PanelEnterStiffness),
    )

    /** Standard exit transition for inline collapsible panels. */
    val PanelExit: ExitTransition = fadeOut(
        animationSpec = tween(ContentFadeOutDuration, easing = Ios27Motion.AppleDefaultEasing),
    ) + shrinkVertically(
        animationSpec = spring(dampingRatio = 0.85f, stiffness = PanelExitStiffness),
    )
}

/**
 * Press-scale click feedback: the element scales down while pressed and
 * springs back on release. Ripple is suppressed; the scale is the feedback.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) SnapAnimations.PressedScale else 1f,
        animationSpec = SnapAnimations.PressSpring,
        label = "bounceClickScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}
