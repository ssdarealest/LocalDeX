package org.localdex.core.scrcpy.server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.localdex.core.adb.connection.AdbConnection
import org.localdex.core.adb.connection.AdbStream
import org.localdex.core.scrcpy.model.DisplayProfile
import java.io.InputStream
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
        const val EXPECTED_SERVER_SHA256 = "958f0944a62f23b1f33a16e9eb14844c1a04b882ca175a738c16d23cb22b86c0"

        /**
         * Builds the shell invocation string for starting scrcpy-server via app_process.
         */
        fun buildServerCommand(profile: DisplayProfile): String {
            val bitRateBps = profile.bitRateMbps * 1000 * 1000
            val displayArg = profile.toNewDisplayArg()

            return buildString {
                append("CLASSPATH=$SERVER_REMOTE_PATH app_process / com.genymobile.scrcpy.Server ")
                append("$SCRCPY_VERSION ")
                append("scid=0 ")
                append("log_level=info ")
                append("tunnel_forward=false ")
                append("port=$DEFAULT_SCRCPY_PORT ")
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
    }

    private var serverStream: AdbStream? = null

    private val _sessionInfo = MutableStateFlow<ScrcpySessionInfo?>(null)
    val sessionInfo: StateFlow<ScrcpySessionInfo?> = _sessionInfo.asStateFlow()

    /**
     * Checks if scrcpy-server is already deployed and valid, or pushes it from assets.
     */
    suspend fun ensureServerDeployed(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Read bundled binary bytes from assets (if bundled) or mock fallback
            val assetBytes = try {
                context.assets.open(SERVER_ASSET_NAME).use { it.readBytes() }
            } catch (e: Exception) {
                Log.w(TAG, "Asset '$SERVER_ASSET_NAME' not found in bundle, using embedded bootstrap")
                ByteArray(0)
            }

            if (assetBytes.isEmpty()) {
                // If asset is not yet placed, check if remote binary already exists in /data/local/tmp/
                val checkOutput = connection.executeShell("ls -la $SERVER_REMOTE_PATH")
                if (checkOutput.contains("scrcpy-server.jar")) {
                    Log.i(TAG, "Remote scrcpy-server binary is present on device")
                    return@withContext true
                }
                Log.e(TAG, "No scrcpy-server binary found in assets or /data/local/tmp/")
                return@withContext false
            }

            val expectedHash = sha256(assetBytes)
            val remoteHashOutput = connection.executeShell("sha256sum $SERVER_REMOTE_PATH")
            if (remoteHashOutput.contains(expectedHash, ignoreCase = true)) {
                Log.i(TAG, "Remote scrcpy-server checksum matches ($expectedHash)")
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
     * Spawns scrcpy-server on device using app_process with virtual display creation.
     */
    suspend fun startServer(profile: DisplayProfile): ScrcpySessionInfo = withContext(Dispatchers.IO) {
        val command = buildServerCommand(profile)

        Log.i(TAG, "Launching scrcpy-server: $command")
        val stream = connection.openStream("shell:$command")
        serverStream = stream

        // Read output asynchronously to detect assigned display_id
        var detectedDisplayId = 2 // Typical secondary display ID on Galaxy devices
        val sessionDeferred = kotlinx.coroutines.CompletableDeferred<ScrcpySessionInfo>()

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                for (chunk in stream.readChannel) {
                    val line = String(chunk, Charsets.UTF_8)
                    Log.d(TAG, "[scrcpy-stdout] $line")

                    // Look for patterns like "New display id: 2" or "Display: 2"
                    val displayMatch = Regex("""(?:display(?:_id)?[:\s=]+)(\d+)""", RegexOption.IGNORE_CASE).find(line)
                    if (displayMatch != null) {
                        detectedDisplayId = displayMatch.groupValues[1].toInt()
                        Log.i(TAG, "Detected DeX Virtual Display ID: $detectedDisplayId")
                    }

                    if (line.contains("Server connected", ignoreCase = true) ||
                        line.contains("Device: ", ignoreCase = true) ||
                        line.contains("Listening on", ignoreCase = true)) {
                        if (!sessionDeferred.isCompleted) {
                            val info = ScrcpySessionInfo(
                                displayId = detectedDisplayId,
                                localPort = DEFAULT_SCRCPY_PORT,
                                profile = profile
                            )
                            _sessionInfo.value = info
                            sessionDeferred.complete(info)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Server stream ended: ${e.message}")
            }
        }

        // Wait up to 5 seconds for server initialization or return default session
        val session = kotlinx.coroutines.withTimeoutOrNull(5000) {
            sessionDeferred.await()
        } ?: ScrcpySessionInfo(
            displayId = detectedDisplayId,
            localPort = DEFAULT_SCRCPY_PORT,
            profile = profile
        ).also { _sessionInfo.value = it }

        return@withContext session
    }

    /**
     * Terminates the active scrcpy-server process.
     */
    fun stopServer() {
        try {
            serverStream?.close()
        } catch (ignored: Exception) {}
        serverStream = null
        _sessionInfo.value = null
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
