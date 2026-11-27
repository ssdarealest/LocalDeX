package org.localdex.ui.foldable

import android.app.Activity
import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class FoldablePostureTrackerTest {

    private lateinit var activity: Activity
    private lateinit var tracker: FoldablePostureTracker

    @Before
    fun setUp() {
        activity = mockk(relaxed = true)
        tracker = FoldablePostureTracker(activity)
    }

    @Test
    fun calculateFoldableState_noFoldingFeature_returnsNormalPosture() {
        val layoutInfo = mockk<WindowLayoutInfo> {
            every { displayFeatures } returns emptyList()
        }

        val state = tracker.calculateFoldableState(layoutInfo)
        assertThat(state.posture).isEqualTo(DevicePosture.NORMAL)
        assertThat(state.suggestedProfileId).isNull()
    }

    @Test
    fun calculateFoldableState_halfOpenedHorizontal_returnsTabletopPosture() {
        val foldingFeature = mockk<FoldingFeature> {
            every { state } returns FoldingFeature.State.HALF_OPENED
            every { orientation } returns FoldingFeature.Orientation.HORIZONTAL
            every { bounds } returns Rect(0, 900, 1080, 920)
            every { isSeparating } returns true
        }

        val layoutInfo = mockk<WindowLayoutInfo> {
            every { displayFeatures } returns listOf(foldingFeature)
        }

        val state = tracker.calculateFoldableState(layoutInfo)
        assertThat(state.posture).isEqualTo(DevicePosture.TABLETOP)
        assertThat(state.isSeparating).isTrue()
        assertThat(state.suggestedProfileId).isEqualTo("fold_inner")
    }

    @Test
    fun calculateFoldableState_halfOpenedVertical_returnsBookPosture() {
        val foldingFeature = mockk<FoldingFeature> {
            every { state } returns FoldingFeature.State.HALF_OPENED
            every { orientation } returns FoldingFeature.Orientation.VERTICAL
            every { bounds } returns Rect(900, 0, 920, 1800)
            every { isSeparating } returns true
        }

        val layoutInfo = mockk<WindowLayoutInfo> {
            every { displayFeatures } returns listOf(foldingFeature)
        }

        val state = tracker.calculateFoldableState(layoutInfo)
        assertThat(state.posture).isEqualTo(DevicePosture.BOOK)
        assertThat(state.suggestedProfileId).isEqualTo("fold_inner")
    }

    @Test
    fun calculateFoldableState_flat_returnsUnfoldedPosture() {
        val foldingFeature = mockk<FoldingFeature> {
            every { state } returns FoldingFeature.State.FLAT
            every { orientation } returns FoldingFeature.Orientation.VERTICAL
            every { bounds } returns Rect(900, 0, 900, 1800)
            every { isSeparating } returns false
        }

        val layoutInfo = mockk<WindowLayoutInfo> {
            every { displayFeatures } returns listOf(foldingFeature)
        }

        val state = tracker.calculateFoldableState(layoutInfo)
        assertThat(state.posture).isEqualTo(DevicePosture.UNFOLDED)
        assertThat(state.suggestedProfileId).isEqualTo("fold_inner")
    }
}
