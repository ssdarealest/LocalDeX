package org.localdex.core.adb.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

sealed class AdbEndpoint {
    data class Pairing(val host: InetAddress, val port: Int) : AdbEndpoint()
    data class Connect(val host: InetAddress, val port: Int) : AdbEndpoint()
}

data class DiscoveryState(
    val isScanning: Boolean = false,
    val pairingEndpoint: AdbEndpoint.Pairing? = null,
    val connectEndpoint: AdbEndpoint.Connect? = null,
    val lastError: String? = null
)

/**
 * Discovers Android 11+ Wireless Debugging pairing and connect ports via mDNS (NsdManager).
 */
class AdbDiscoveryManager(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveryState = MutableStateFlow(DiscoveryState())
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private var pairingListener: NsdManager.DiscoveryListener? = null
    private var connectListener: NsdManager.DiscoveryListener? = null

    companion object {
        private const val TAG = "AdbDiscoveryManager"
        const val SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp."
        const val SERVICE_TYPE_CONNECT = "_adb-tls-connect._tcp."
    }

    @Synchronized
    fun startDiscovery() {
        if (_discoveryState.value.isScanning) {
            Log.d(TAG, "Discovery already running")
            return
        }

        _discoveryState.value = DiscoveryState(isScanning = true)

        try {
            startPairingDiscovery()
            startConnectDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            _discoveryState.value = _discoveryState.value.copy(
                isScanning = false,
                lastError = e.message
            )
        }
    }

    @Synchronized
    fun stopDiscovery() {
        if (!_discoveryState.value.isScanning) return

        try {
            pairingListener?.let { nsdManager.stopServiceDiscovery(it) }
            connectListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping discovery", e)
        } finally {
            pairingListener = null
            connectListener = null
            _discoveryState.value = _discoveryState.value.copy(isScanning = false)
        }
    }

    private fun startPairingDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Pairing discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Pairing service candidate found: ${service.serviceName}")
                resolveService(service, isPairing = true)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Pairing service lost: ${service.serviceName}")
                _discoveryState.value = _discoveryState.value.copy(pairingEndpoint = null)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Pairing discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Pairing discovery failed to start: $errorCode")
                _discoveryState.value = _discoveryState.value.copy(lastError = "Pairing discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Pairing discovery failed to stop: $errorCode")
            }
        }
        pairingListener = listener
        nsdManager.discoverServices(SERVICE_TYPE_PAIRING, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun startConnectDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Connect discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Connect service candidate found: ${service.serviceName}")
                resolveService(service, isPairing = false)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Connect service lost: ${service.serviceName}")
                _discoveryState.value = _discoveryState.value.copy(connectEndpoint = null)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Connect discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Connect discovery failed to start: $errorCode")
                _discoveryState.value = _discoveryState.value.copy(lastError = "Connect discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Connect discovery failed to stop: $errorCode")
            }
        }
        connectListener = listener
        nsdManager.discoverServices(SERVICE_TYPE_CONNECT, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun resolveService(service: NsdServiceInfo, isPairing: Boolean) {
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host ?: InetAddress.getByName("127.0.0.1")
                val port = serviceInfo.port
                Log.i(TAG, "Resolved ${if (isPairing) "PAIRING" else "CONNECT"} endpoint: $host:$port")

                _discoveryState.value = if (isPairing) {
                    _discoveryState.value.copy(pairingEndpoint = AdbEndpoint.Pairing(host, port))
                } else {
                    _discoveryState.value.copy(connectEndpoint = AdbEndpoint.Connect(host, port))
                }
            }
        })
    }
}
