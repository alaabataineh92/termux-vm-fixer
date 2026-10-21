package io.github.alaabataineh92.termuxvmfixer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alaabataineh92.termuxvmfixer.core.device.log.AppLogger
import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel
import io.github.alaabataineh92.termuxvmfixer.core.model.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val appLogger: AppLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(LogViewerState())
    val state: StateFlow<LogViewerState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val threshold = _state.value.threshold

            val snapshot = withContext(Dispatchers.Default) {
                val entries = appLogger.ring.entries()
                    .filter { it.level.isEnabledAt(threshold) }
                    .map { it.toUiEntry() }

                entries to appLogger.droppedLines()
            }

            _state.update {
                it.copy(entries = snapshot.first, droppedLines = snapshot.second)
            }
        }
    }

    fun setThreshold(level: LogLevel) {
        appLogger.setLevel(level)
        _state.update { it.copy(threshold = level) }
        refresh()
    }

    fun clear() {
        appLogger.clearRing()
        refresh()
    }

    fun exportText(): String = appLogger.export()

    private fun io.github.alaabataineh92.termuxvmfixer.core.common.log.RingBufferLogger.Entry.toUiEntry() =
        LogEntry(
            message = format(),
            level = level,
            timestampMillis = timestampMillis,
        )
}

data class LogViewerState(
    val entries: List<LogEntry> = emptyList(),
    val threshold: LogLevel = LogLevel.DEBUG,
    val droppedLines: Int = 0,
)
