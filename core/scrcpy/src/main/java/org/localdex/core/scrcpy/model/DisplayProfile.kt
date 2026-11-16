package org.localdex.core.scrcpy.model

data class DisplayProfile(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val dpi: Int,
    val maxFps: Int = 60,
    val bitRateMbps: Int = 16,
    val codec: VideoCodec = VideoCodec.H264,
    val isCustom: Boolean = false
) {
    enum class VideoCodec(val scrcpyName: String, val mimeType: String) {
        H264("h264", "video/avc"),
        H265("h265", "video/hevc")
    }

    /**
     * Formats into scrcpy --new-display argument: <width>x<height>/<dpi>
     */
    fun toNewDisplayArg(): String = "${width}x${height}/$dpi"

    /**
     * Calculated logical width in density-independent pixels (dp).
     */
    val logicalWidthDp: Int get() = (width * 160f / dpi).toInt()

    /**
     * Calculated logical height in density-independent pixels (dp).
     */
    val logicalHeightDp: Int get() = (height * 160f / dpi).toInt()

    /**
     * Aspect ratio width / height as float.
     */
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height.toFloat() else 16f / 9f

    companion object {
        val PRESET_NATIVE = DisplayProfile(
            id = "native",
            name = "Native Aspect (Foldable / Slab)",
            width = 2176,
            height = 1812,
            dpi = 360,
            maxFps = 60,
            bitRateMbps = 20
        )

        val PRESET_FOLD_INNER = DisplayProfile(
            id = "fold_inner",
            name = "Galaxy Z Fold (Inner Unfolded)",
            width = 2176,
            height = 1812,
            dpi = 360,
            maxFps = 60,
            bitRateMbps = 24
        )

        val PRESET_FOLD_COVER = DisplayProfile(
            id = "fold_cover",
            name = "Galaxy Z Fold (Cover Screen)",
            width = 2316,
            height = 904,
            dpi = 380,
            maxFps = 60,
            bitRateMbps = 18
        )

        val PRESET_1080P = DisplayProfile(
            id = "1080p",
            name = "1080p Standard (16:9)",
            width = 1920,
            height = 1080,
            dpi = 240,
            maxFps = 60,
            bitRateMbps = 16
        )

        val PRESET_4_3 = DisplayProfile(
            id = "4_3",
            name = "4:3 Productivity",
            width = 1920,
            height = 1440,
            dpi = 240,
            maxFps = 60,
            bitRateMbps = 18
        )

        val PRESET_ULTRAWIDE = DisplayProfile(
            id = "ultrawide",
            name = "Ultrawide (21:9)",
            width = 2560,
            height = 1080,
            dpi = 280,
            maxFps = 60,
            bitRateMbps = 24
        )

        val DEFAULT_PRESETS = listOf(
            PRESET_1080P,
            PRESET_NATIVE,
            PRESET_FOLD_INNER,
            PRESET_FOLD_COVER,
            PRESET_4_3,
            PRESET_ULTRAWIDE
        )
    }
}
