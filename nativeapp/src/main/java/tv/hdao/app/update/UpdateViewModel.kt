package tv.hdao.app.update

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: UpdateRelease) : UpdateUiState
    data class Downloading(val release: UpdateRelease, val progress: Int) : UpdateUiState
    data class Ready(val release: UpdateRelease, val apk: File) : UpdateUiState
    data class Failed(val release: UpdateRelease?, val message: String) : UpdateUiState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = UpdateManager(application.applicationContext)
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()

    fun check() {
        if (mutableState.value != UpdateUiState.Hidden) return
        viewModelScope.launch {
            mutableState.value = UpdateUiState.Checking
            mutableState.value = runCatching { manager.checkForUpdate() }
                .getOrNull()
                ?.let(UpdateUiState::Available)
                ?: UpdateUiState.Hidden
        }
    }

    fun download(release: UpdateRelease) {
        if (mutableState.value is UpdateUiState.Downloading) return
        viewModelScope.launch {
            mutableState.value = UpdateUiState.Downloading(release, 0)
            mutableState.value = try {
                val apk = manager.downloadAndVerify(release) { progress ->
                    mutableState.value = UpdateUiState.Downloading(release, progress)
                }
                UpdateUiState.Ready(release, apk)
            } catch (error: Exception) {
                UpdateUiState.Failed(release, error.message ?: "更新下载失败")
            }
        }
    }

    fun dismiss() {
        if (mutableState.value !is UpdateUiState.Downloading) {
            mutableState.value = UpdateUiState.Hidden
        }
    }

    fun failInstall(release: UpdateRelease, message: String) {
        mutableState.value = UpdateUiState.Failed(release, message)
    }

    fun needsInstallPermission(): Boolean = manager.needsInstallPermission()
    fun installPermissionIntent(): Intent = manager.installPermissionIntent()
    fun installIntent(apk: File): Intent = manager.installIntent(apk)
}
