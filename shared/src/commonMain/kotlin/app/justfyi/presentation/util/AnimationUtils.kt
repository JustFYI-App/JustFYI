package app.justfyi.presentation.util

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically

/**
 * Animation utilities with reduce motion support for WCAG 2.1 AA compliance.
 *
 * This utility provides animation specs and transitions that respect the
 * system's reduce motion accessibility preference. When reduce motion is
 * enabled, animations are instant (snap) instead of animated transitions.
 *
 * Usage:
 * ```kotlin
 * val reduceMotion = rememberReduceMotionPreference()
 *
 * // For state animations
 * val backgroundColor by animateColorAsState(
 *     targetValue = if (isSelected) primary else surface,
 *     animationSpec = reducedAnimationSpec(reduceMotion.value)
 * )
 *
 * // For AnimatedVisibility
 * AnimatedVisibility(
 *     visible = isVisible,
 *     enter = reducedEnterTransition(reduceMotion.value),
 *     exit = reducedExitTransition(reduceMotion.value)
 * )
 * ```
 */
object AnimationUtils {
    const val DEFAULT_ANIMATION_DURATION_MS = 300
}

/**
 * Returns an animation spec that respects reduce motion preference.
 * When reduce motion is enabled, returns snap() for instant transitions.
 * When disabled, returns a tween with the specified duration.
 *
 * @param reduceMotion Whether reduce motion is enabled
 * @param durationMs Animation duration when reduce motion is disabled
 * @return FiniteAnimationSpec that either snaps or animates
 */
fun <T> reducedAnimationSpec(
    reduceMotion: Boolean,
    durationMs: Int = AnimationUtils.DEFAULT_ANIMATION_DURATION_MS,
): FiniteAnimationSpec<T> =
    if (reduceMotion) {
        snap()
    } else {
        tween(durationMillis = durationMs)
    }

/**
 * Returns an enter transition that respects reduce motion preference.
 * When reduce motion is enabled, returns an instant (no animation) transition.
 * When disabled, returns fadeIn + slideInVertically.
 *
 * @param reduceMotion Whether reduce motion is enabled
 * @return EnterTransition appropriate for the motion preference
 */
fun reducedEnterTransition(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        // Instant entrance - no visible animation
        fadeIn(animationSpec = snap())
    } else {
        fadeIn(animationSpec = tween(AnimationUtils.DEFAULT_ANIMATION_DURATION_MS)) +
            slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(AnimationUtils.DEFAULT_ANIMATION_DURATION_MS),
            )
    }

/**
 * Returns a vertical expand enter transition that respects reduce motion preference.
 * When reduce motion is enabled, returns an instant transition.
 * When disabled, returns fadeIn + expandVertically.
 *
 * @param reduceMotion Whether reduce motion is enabled
 * @return EnterTransition appropriate for the motion preference
 */
fun reducedExpandEnterTransition(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(animationSpec = snap())
    } else {
        fadeIn(animationSpec = tween(AnimationUtils.DEFAULT_ANIMATION_DURATION_MS)) +
            expandVertically(animationSpec = tween(AnimationUtils.DEFAULT_ANIMATION_DURATION_MS))
    }

/**
 * Returns a vertical shrink exit transition that respects reduce motion preference.
 * When reduce motion is enabled, returns an instant transition.
 * When disabled, returns fadeOut + shrinkVertically.
 *
 * @param reduceMotion Whether reduce motion is enabled
 * @return ExitTransition appropriate for the motion preference
 */
fun reducedShrinkExitTransition(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        fadeOut(animationSpec = snap())
    } else {
        fadeOut(animationSpec = tween(AnimationUtils.DEFAULT_ANIMATION_DURATION_MS)) +
            shrinkVertically(animationSpec = tween(AnimationUtils.DEFAULT_ANIMATION_DURATION_MS))
    }

/**
 * Returns the appropriate floating animation offset for decorative icons.
 * When reduce motion is enabled, returns 0f (static).
 * When disabled, returns the specified offset for floating effect.
 *
 * @param reduceMotion Whether reduce motion is enabled
 * @param floatOffset The offset to use when animations are enabled
 * @return Float offset to use for the floating animation
 */
fun reducedFloatOffset(
    reduceMotion: Boolean,
    floatOffset: Float = -10f,
): Float = if (reduceMotion) 0f else floatOffset
