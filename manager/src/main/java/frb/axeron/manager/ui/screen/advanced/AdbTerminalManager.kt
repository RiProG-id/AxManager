package frb.axeron.manager.ui.screen.advanced

import android.app.Application
import android.provider.Settings
import android.util.Log
import frb.axeron.adb.AdbClient
import frb.axeron.adb.AdbKey
import frb.axeron.adb.PreferenceAdbKeyStore
import frb.axeron.adb.util.AdbEnvironment
import frb.axeron.api.core.AxeronSettings
import frb.axeron.api.core.Starter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdbTerminalManager(
    private val application: Application,
    private val scope: CoroutineScope
) {
    private var adbClient: AdbClient? = null

    private val _status = MutableStateFlow("Disconnected")
    val status: StateFlow<String> = _status

    private val _output = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val output: SharedFlow<ByteArray> = _output

    suspend fun connect() {
        Log.i("AdbTerminalManager", "LOG: Starting ADB connection")
        try {
            val port = withContext(Dispatchers.IO) { AdbEnvironment.getAdbTcpPort() }
            if (port <= 0) {
                _status.value = "ADB Port not found"
                return
            }

            val keyStore = PreferenceAdbKeyStore(
                AxeronSettings.getPreferences(),
                Settings.Global.getString(application.contentResolver, Starter.KEY_PAIR)
            )
            val key = AdbKey(keyStore, "axeron")

            adbClient = AdbClient(key, port)

            // Status Collection
            scope.launch {
                adbClient?.connectionStatus?.collect { _status.value = it }
            }

            // Output Collection
            scope.launch {
                adbClient?.shellOutput?.collect { _output.emit(it) }
            }

            withContext(Dispatchers.IO) {
                adbClient?.connect()
                adbClient?.startShell()
            }
        } catch (t: Throwable) {
            Log.e("AdbTerminalManager", "LOG: ADB connect failed", t)
            _status.value = "Failed: ${t.message}"
        }
    }

    fun disconnect() {
        adbClient?.close()
        adbClient = null
        _status.value = "Disconnected"
    }

    fun sendInput(text: String) {
        adbClient?.sendShellRaw(text.toByteArray())
    }

    fun sendRaw(data: ByteArray) {
        adbClient?.sendShellRaw(data)
    }

    fun sendCommand(command: String) {
        adbClient?.sendShellCommand(command)
    }
}
