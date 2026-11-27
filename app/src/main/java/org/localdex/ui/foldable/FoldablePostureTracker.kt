package org.localdex.ui.foldable

import android.app.Activity
import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Physical posture of a foldable or dual-screen device.
 */
enum class DevicePosture {
    /** Standard slab device or Galaxy Z Fold with cover screen closed */
    NORMAL,

    /** Folded in half with horizontal hinge on a surface (laptop / tabletop mode) */
    TABLETOP,

    /** Folded in half with vertical hinge (book posture) */
    BOOK,

    /** Fully unfolded flat large inner screen */
    UNFOLDED
}

/**
 * State representing current foldable hinge status and recommendations.
 */
data class FoldableState(
    val posture: DevicePosture = DevicePosture.NORMAL,
    val hingeBounds: Rect? = null,
    val isSeparating: Boolean = false,
    val suggestedProfileId: String? = null
)

/**
 * Tracks foldable hinge state, postures, and profile recommendations using Jetpack WindowManager.
 */
class FoldablePostureTracker(private val activity: Activity) {

    val postureFlow: Flow<FoldableState> = WindowInfoTracker.getOrCreate(activity)
        .windowLayoutInfo(activity)
        .map { layoutInfo ->
            calculateFoldableState(layoutInfo)
        }

    fun calculateFoldableState(layoutInfo: WindowLayoutInfo): FoldableState {
        val foldingFeature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        if (foldingFeature == null) {
            return FoldableState(
                posture = DevicePosture.NORMAL,
                suggestedProfileId = null
            )
        }

        val posture = when {
            foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                    foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL -> DevicePosture.TABLETOP

            foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                    foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL -> DevicePosture.BOOK

            foldingFeature.state == FoldingFeature.State.FLAT -> DevicePosture.UNFOLDED

            else -> DevicePosture.NORMAL
        }

        val suggestedProfile = when (posture) {
            DevicePosture.UNFOLDED, DevicePosture.TABLETOP, DevicePosture.BOOK -> "fold_inner"
            DevicePosture.NORMAL -> "fold_cover"
        }

        return FoldableState(
            posture = posture,
            hingeBounds = foldingFeature.bounds,
            isSeparating = foldingFeature.isSeparating,
            suggestedProfileId = suggestedProfile
        )
    }
}
