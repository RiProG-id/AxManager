package frb.axeron.manager.ui.screen.advanced

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AdvancedTerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val adbManager = AdbTerminalManager(application, viewModelScope)
    val terminalEmulator = TerminalEmulator()

    val adbStatus get() = adbManager.status

    var isCtrlPressed by mutableStateOf(false)
    var isAltPressed by mutableStateOf(false)

    init {
        viewModelScope.launch {
            adbManager.output.collect { data ->
                terminalEmulator.append(data)
            }
        }
        connectAdb()
    }

    fun connectAdb() {
        viewModelScope.launch {
            adbManager.connect()
        }
    }

    fun disconnectAdb() {
        adbManager.disconnect()
    }

    fun sendInput(text: String) {
        adbManager.sendInput(text)
    }

    fun sendRaw(data: ByteArray) {
        adbManager.sendRaw(data)
    }

    fun sendSpecialKey(key: String) {
        try {
            if (key == "CTRL") {
                isCtrlPressed = !isCtrlPressed
                return
            }
            if (key == "ALT") {
                isAltPressed = !isAltPressed
                return
            }

            var data = key.toByteArray()
            if (isCtrlPressed) {
                if (key.length == 1) {
                    val c = key[0].uppercaseChar()
                    if (c in 'A'..'Z') {
                        data = byteArrayOf((c.code - 'A'.code + 1).toByte())
                    }
                }
                isCtrlPressed = false
            }
            if (isAltPressed) {
                val newData = ByteArray(data.size + 1)
                newData[0] = 0x1b
                System.arraycopy(data, 0, newData, 1, data.size)
                data = newData
                isAltPressed = false
            }
            adbManager.sendRaw(data)
        } catch (e: Exception) {
            Log.e("AdvancedTerminalViewModel", "Failed to send special key", e)
        }
    }

    fun clear() {
        terminalEmulator.clear()
    }

    override fun onCleared() {
        super.onCleared()
        disconnectAdb()
    }
}
