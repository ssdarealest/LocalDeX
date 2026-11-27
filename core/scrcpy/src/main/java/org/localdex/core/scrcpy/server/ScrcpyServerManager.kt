package org.localdex.core.scrcpy.server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.localdex.core.adb.connection.AdbConnection
import org.localdex.core.adb.connection.AdbStream
import org.localdex.core.scrcpy.model.DisplayProfile
import java.security.MessageDigest

data class ScrcpySessionInfo(
    val displayId: Int,
    val localPort: Int = 27183,
    val profile: DisplayProfile
)

/**
 * Manages scrcpy-server asset deployment and execution via loopback ADB shell.
 */
class ScrcpyServerManager(
    private val context: Context,
    private val connection: AdbConnection
) {
    companion object {
        private const val TAG = "ScrcpyServerManager"
        const val SERVER_REMOTE_PATH = "/data/local/tmp/scrcpy-server.jar"
        const val SERVER_ASSET_NAME = "scrcpy-server"
        const val SCRCPY_VERSION = "3.1"
        const val DEFAULT_SCRCPY_PORT = 27183
        const val DEFAULT_SCID = -1
        const val EXPECTED_SERVER_SHA256 = "958f0944a62f23b1f33a16e9eb14844c1a04b882ca175a738c16d23cb22b86c0"

        private val DISPLAY_ID_PATTERNS = listOf(
            Regex("""(?:display(?:_id|\s+id)|mDisplayId|Display Id)\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""\(id=(\d+)\)""", RegexOption.IGNORE_CASE),
            Regex("""\bid=(\d+)\b""", RegexOption.IGNORE_CASE)
        )

        /**
         * Returns the ADB UNIX abstract socket destination for [scid].
         */
        fun socketDestination(scid: Int = DEFAULT_SCID): String {
            return if (scid < 0) {
                "localabstract:scrcpy"
            } else {
                "localabstract:scrcpy_%08x".format(scid)
            }
        }

        /**
         * Builds the shell invocation string for starting scrcpy-server via app_process
         * in forward-tunnel mode (localabstract socket bridged over loopback ADB).
         */
        fun buildServerCommand(profile: DisplayProfile, scid: Int = DEFAULT_SCID): String {
            val bitRateBps = profile.bitRateMbps * 1000 * 1000
            val displayArg = profile.toNewDisplayArg()
            val scidArg = if (scid < 0) "-1" else "%x".format(scid)

            return buildString {
                append("CLASSPATH=$SERVER_REMOTE_PATH app_process / com.genymobile.scrcpy.Server ")
                append("$SCRCPY_VERSION ")
                append("scid=$scidArg ")
                append("log_level=info ")
                append("tunnel_forward=true ")
                append("send_dummy_byte=true ")
                append("new_display=$displayArg ")
                append("video_codec=${profile.codec.scrcpyName} ")
                append("video_bit_rate=$bitRateBps ")
                append("max_fps=${profile.maxFps} ")
                if (profile.audioEnabled) {
                    append("audio=true ")
                    append("audio_codec=${profile.audioCodec.scrcpyName} ")
                } else {
                    append("audio=false ")
                }
                append("control=true ")
                append("stay_awake=true ")
                append("power_off_on_close=false ")
                append("cleanup=true")
            }
        }

        internal fun parseDisplayId(text: String): Int? {
            for (pattern in DISPLAY_ID_PATTERNS) {
                val id = pattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (id != null && id in 1..999) {
                    return id
                }
            }
            return null
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverStream: AdbStream? = null
    private var loopbackBridge: ScrcpyLoopbackBridge? = null
    private var monitorJob: Job? = null

    private val _sessionInfo = MutableStateFlow<ScrcpySessionInfo?>(null)
    val sessionInfo: StateFlow<ScrcpySessionInfo?> = _sessionInfo.asStateFlow()

    /**
     * Checks if scrcpy-server is already deployed and matches [EXPECTED_SERVER_SHA256],
     * or pushes the SHA-256 verified binary from assets.
     */
    suspend fun ensureServerDeployed(): Boolean = withContext(Dispatchers.IO) {
        try {
            val assetBytes = try {
                context.assets.open(SERVER_ASSET_NAME).use { it.readBytes() }
            } catch (e: Exception) {
                Log.w(TAG, "Asset '$SERVER_ASSET_NAME' not found in bundle")
                ByteArray(0)
            }

            if (assetBytes.isEmpty()) {
                val remoteHashOutput = connection.executeShell("sha256sum $SERVER_REMOTE_PATH")
                if (remoteHashOutput.contains(EXPECTED_SERVER_SHA256, ignoreCase = true)) {
                    Log.i(TAG, "Remote scrcpy-server checksum matches expected SHA-256")
                    return@withContext true
                }
                Log.e(TAG, "No verified scrcpy-server binary found in assets or $SERVER_REMOTE_PATH")
                return@withContext false
            }

            val actualAssetHash = sha256(assetBytes)
            if (!actualAssetHash.equals(EXPECTED_SERVER_SHA256, ignoreCase = true)) {
                Log.e(
                    TAG,
                    "Bundled scrcpy-server SHA-256 mismatch: expected $EXPECTED_SERVER_SHA256, got $actualAssetHash"
                )
                return@withContext false
            }

            val remoteHashOutput = connection.executeShell("sha256sum $SERVER_REMOTE_PATH")
            if (remoteHashOutput.contains(EXPECTED_SERVER_SHA256, ignoreCase = true)) {
                Log.i(TAG, "Remote scrcpy-server checksum matches ($EXPECTED_SERVER_SHA256)")
                return@withContext true
            }

            Log.i(TAG, "Pushing scrcpy-server to $SERVER_REMOTE_PATH (${assetBytes.size} bytes)...")
            val pushed = connection.pushFile(assetBytes, SERVER_REMOTE_PATH, permissionsMode = 493) // 0755
            if (pushed) {
                connection.executeShell("chmod 755 $SERVER_REMOTE_PATH")
                Log.i(TAG, "Successfully deployed and chmodded scrcpy-server")
            }
            return@withContext pushed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deploy scrcpy-server", e)
            return@withContext false
        }
    }

    /**
     * Spawns scrcpy-server on device using app_process with virtual display creation
     * and exposes a local loopback TCP bridge backed by ADB `localabstract:scrcpy`.
     */
    suspend fun startServer(
        profile: DisplayProfile,
        scid: Int = DEFAULT_SCID
    ): ScrcpySessionInfo = withContext(Dispatchers.IO) {
        stopServer()

        val bridge = ScrcpyLoopbackBridge(
            connection = connection,
            socketDestination = socketDestination(scid)
        )
        val localPort = bridge.start()
        loopbackBridge = bridge

        val command = buildServerCommand(profile, scid)
        Log.i(TAG, "Launching scrcpy-server: $command (bridge=127.0.0.1:$localPort)")

        val stream = try {
            connection.openStream("shell:$command")
        } catch (e: Exception) {
            bridge.close()
            loopbackBridge = null
            throw e
        }
        serverStream = stream

        val initialInfo = ScrcpySessionInfo(
            displayId = 2,
            localPort = localPort,
            profile = profile
        )
        _sessionInfo.value = initialInfo

        monitorJob = scope.launch {
            monitorServerStdout(stream, localPort, profile)
        }

        return@withContext initialInfo
    }

    private suspend fun monitorServerStdout(
        stream: AdbStream,
        localPort: Int,
        profile: DisplayProfile
    ) {
        var detectedDisplayId = 2
        var explicitIdFound = false
        var appliedFreeformDisplayId: Int? = null
        val lineBuffer = StringBuilder()

        suspend fun ensureFreeformWindowingMode() {
            if (!explicitIdFound) {
                val dumpsysOutput = try {
                    connection.executeShell("dumpsys display | grep -B 4 -A 4 scrcpy")
                } catch (_: Exception) {
                    ""
                }
                parseDisplayId(dumpsysOutput)?.let { queriedId ->
                    detectedDisplayId = queriedId
                    explicitIdFound = true
                }
            }

            val updatedInfo = ScrcpySessionInfo(
                displayId = detectedDisplayId,
                localPort = localPort,
                profile = profile
            )
            _sessionInfo.value = updatedInfo

            if (appliedFreeformDisplayId != detectedDisplayId) {
                appliedFreeformDisplayId = detectedDisplayId
                try {
                    connection.executeShell("wm set-display-windowing-mode -d $detectedDisplayId 5")
                    Log.i(TAG, "Configured freeform windowing mode on display $detectedDisplayId")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to configure freeform windowing mode on display $detectedDisplayId: ${e.message}")
                }
            }
        }

        try {
            for (chunk in stream.readChannel) {
                lineBuffer.append(String(chunk, Charsets.UTF_8))
                while (true) {
                    val newlineIndex = lineBuffer.indexOf('\n')
                    if (newlineIndex < 0) break
                    val line = lineBuffer.substring(0, newlineIndex).trimEnd('\r')
                    lineBuffer.delete(0, newlineIndex + 1)
                    if (line.isBlank()) continue

                    Log.d(TAG, "[scrcpy-stdout] $line")
                    val parsedId = parseDisplayId(line)
                    if (parsedId != null) {
                        detectedDisplayId = parsedId
                        explicitIdFound = true
                        Log.i(TAG, "Detected DeX Virtual Display ID: $detectedDisplayId")
                        _sessionInfo.value = ScrcpySessionInfo(
                            displayId = detectedDisplayId,
                            localPort = localPort,
                            profile = profile
                        )
                    }

                    if (parsedId != null || line.contains("New display", ignoreCase = true)) {
                        ensureFreeformWindowingMode()
                    }
                }
            }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            Log.w(TAG, "Server stream ended: ${e.message}")
        }
    }

    /**
     * Terminates the active scrcpy-server process and its loopback socket bridge.
     */
    fun stopServer() {
        monitorJob?.cancel()
        monitorJob = null
        try {
            serverStream?.close()
        } catch (_: Exception) {}
        serverStream = null
        try {
            loopbackBridge?.close()
        } catch (_: Exception) {}
        loopbackBridge = null
        _sessionInfo.value = null
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
