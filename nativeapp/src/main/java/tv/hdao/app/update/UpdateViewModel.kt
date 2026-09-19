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
    private val preferences =
        application.applicationContext.getSharedPreferences("update_check", Application.MODE_PRIVATE)
    private var lastAttemptFailed = false
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()

    /**
     * Background update check, called on launch by UpdateCoordinator.
     *
     * The user never triggers this from the UI: it stays silent unless a newer
     * release exists, in which case the dialog offers "立即更新 / 稍后".
     * [force] bypasses the check interval, and is used by the retry button in a
     * failure dialog.
     *
     * The interval exists because the app returns to the home screen many times
     * per session; without it every return would hit the network. Failures are
     * retried sooner than successes, so a TV that had no network yet when it was
     * switched on still picks the update up a few minutes later.
     */
    fun check(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force) {
            val last = preferences.getLong(KEY_LAST_CHECK, 0L)
            val elapsed = now - last
            val required = if (lastAttemptFailed) RETRY_INTERVAL_MS else CHECK_INTERVAL_MS
            if (elapsed in 0 until required) return
        }
        val current = mutableState.value
        // Never interrupt a check, a download, or an update that is downloaded
        // and waiting for the system installer.
        if (current is UpdateUiState.Checking ||
            current is UpdateUiState.Downloading ||
            current is UpdateUiState.Ready
        ) {
            return
        }
        if (!force && current != UpdateUiState.Hidden) return
        viewModelScope.launch {
            mutableState.value = UpdateUiState.Checking
            // A package that an earlier session downloaded and verified is still in
            // the cache. Offer it instead of downloading the same bytes again, and
            // keep offering it when GitHub cannot be reached at all.
            val cached = runCatching { manager.cachedUpdate() }.getOrNull()
            val outcome = runCatching {
                manager.checkForUpdate(runCatching { manager.mirrorSettings() }.getOrNull())
            }
            val release = outcome.getOrNull()
            mutableState.value = when {
                release != null -> {
                    if (cached != null && cached.first.version == release.version) {
                        UpdateUiState.Ready(cached.first, cached.second)
                    } else {
                        UpdateUiState.Available(release)
                    }
                }
                // Nothing newer published — but a pending package may still be on
                // disk, and a check that failed must not hide it either.
                else -> cached?.let { UpdateUiState.Ready(it.first, it.second) }
                    ?: UpdateUiState.Hidden
            }
            lastAttemptFailed = outcome.isFailure
            preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
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

    private companion object {
        /** Six hours: often enough to pick up a release the same day, rare enough
         *  to stay invisible in the network logs of a TV that runs all evening. */
        const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

        /** After a failed attempt (no network yet, mirror down) try again sooner. */
        const val RETRY_INTERVAL_MS = 10L * 60L * 1000L
        const val KEY_LAST_CHECK = "lastCheckAt"
    }
}
