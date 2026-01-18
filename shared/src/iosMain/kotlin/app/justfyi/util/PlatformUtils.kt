@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package app.justfyi.util

import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970
import kotlin.native.Platform

/**
 * iOS implementation of currentTimeMillis.
 * Uses NSDate to get the current time in milliseconds since epoch.
 */
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * iOS implementation of UUID generation.
 * Uses NSUUID from Foundation framework.
 */
actual fun generateUuid(): String = NSUUID().UUIDString()

/**
 * iOS implementation of isDebug.
 * Uses Kotlin/Native Platform.isDebugBinary.
 */
actual val isDebug: Boolean = Platform.isDebugBinary
