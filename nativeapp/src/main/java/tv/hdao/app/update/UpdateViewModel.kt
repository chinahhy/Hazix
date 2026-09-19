package tv.hdao.app.update

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hdao.app.BuildConfig
import java.io.File

sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data class Checking(val manual: Boolean) : UpdateUiState
    data class UpToDate(val version: String) : UpdateUiState
    data class Available(val release: UpdateRelease) : UpdateUiState
    data class Downloading(val release: UpdateRelease, val progress: Int) : UpdateUiState
    data class Ready(val release: UpdateRelease, val apk: File) : UpdateUiState
    data class Failed(val release: UpdateRelease?, val message: String) : UpdateUiState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = UpdateManager(application.applicationContext)
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()

    /**
     * @param manual true when the user asked for a check from the navigation bar.
     * A manual check always reports its outcome, including "already up to date"
     * and network failures; the automatic check on launch stays silent unless a
     * newer release is available.
     */
    fun check(manual: Boolean = false) {
        val current = mutableState.value
        // Never interrupt a check, a download, or an update that is downloaded
        // and waiting for the system installer.
        if (current is UpdateUiState.Checking ||
            current is UpdateUiState.Downloading ||
            current is UpdateUiState.Ready
        ) {
            return
        }
        if (!manual && current != UpdateUiState.Hidden) return
        viewModelScope.launch {
            mutableState.value = UpdateUiState.Checking(manual)
            val outcome = runCatching { manager.checkForUpdate() }
            val release = outcome.getOrNull()
            mutableState.value = when {
                outcome.isFailure -> {
                    val message = outcome.exceptionOrNull()?.message ?: "检查更新失败"
                    if (manual) UpdateUiState.Failed(null, message) else UpdateUiState.Hidden
                }
                release == null -> {
                    if (manual) UpdateUiState.UpToDate(BuildConfig.VERSION_NAME) else UpdateUiState.Hidden
                }
                else -> UpdateUiState.Available(release)
            }
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
