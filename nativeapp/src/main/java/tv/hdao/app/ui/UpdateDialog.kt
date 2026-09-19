package tv.hdao.app.ui

import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import tv.hdao.app.update.UpdateRelease
import tv.hdao.app.update.UpdateUiState
import tv.hdao.app.update.UpdateViewModel
import java.io.File

@Composable
fun rememberUpdateViewModel(): UpdateViewModel {
    // LocalActivity rather than LocalContext: casting a Context to an Activity is
    // what lint's ContextCastToActivity check reports.
    val activity = LocalActivity.current as? ComponentActivity
        ?: error("Hazix in-app updates need a ComponentActivity host")
    return remember(activity) { ViewModelProvider(activity)[UpdateViewModel::class.java] }
}

@Composable
fun UpdateCoordinator(updateViewModel: UpdateViewModel) {
    val context = LocalContext.current
    val state by updateViewModel.state.collectAsStateWithLifecycle()
    var pendingApk by remember { mutableStateOf<File?>(null) }
    var pendingRelease by remember { mutableStateOf<UpdateRelease?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val apk = pendingApk
        val release = pendingRelease
        if (apk != null && release != null && !updateViewModel.needsInstallPermission()) {
            try {
                updateViewModel.dismiss()
                context.startActivity(updateViewModel.installIntent(apk))
            } catch (_: ActivityNotFoundException) {
                updateViewModel.failInstall(release, "系统没有可用的安装器")
            }
        } else if (release != null) {
            updateViewModel.failInstall(release, "需要允许 Hazix 安装未知来源应用")
        }
        pendingApk = null
        pendingRelease = null
    }

    // Background check on launch. The delay lets the TV finish bringing its
    // network up: a check that runs before Wi-Fi/Ethernet is ready fails, and the
    // view model would then wait out the retry interval for nothing.
    LaunchedEffect(Unit) {
        delay(UPDATE_CHECK_DELAY_MS)
        updateViewModel.check()
    }

    fun requestInstall(release: UpdateRelease, apk: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && updateViewModel.needsInstallPermission()) {
                pendingApk = apk
                pendingRelease = release
                permissionLauncher.launch(updateViewModel.installPermissionIntent())
            } else {
                updateViewModel.dismiss()
                context.startActivity(updateViewModel.installIntent(apk))
            }
        } catch (_: ActivityNotFoundException) {
            updateViewModel.failInstall(release, "系统没有可用的安装器")
        } catch (error: Exception) {
            updateViewModel.failInstall(release, error.message ?: "无法启动系统安装器")
        }
    }

    when (val current = state) {
        UpdateUiState.Hidden -> Unit
        // The check runs in the background; only a download the user started is
        // worth showing progress for, so Checking renders nothing.
        is UpdateUiState.Checking -> Unit
        is UpdateUiState.Available -> UpdatePrompt(
            title = "发现新版本 v${current.release.version}",
            message = current.release.notes.ifBlank { "新版本已经准备好，可以直接在电视上下载。" },
            confirmLabel = "立即更新",
            dismissLabel = "稍后",
            onConfirm = { updateViewModel.download(current.release) },
            onDismiss = updateViewModel::dismiss,
        )
        is UpdateUiState.Downloading -> UpdatePrompt(
            title = "正在下载 v${current.release.version}",
            message = "下载完成后会自动校验文件和应用签名。",
            progress = current.progress,
            onConfirm = null,
            onDismiss = {},
        )
        is UpdateUiState.Ready -> UpdatePrompt(
            title = "更新包已就绪 v${current.release.version}",
            message = "安装包已下载并通过校验，接下来由 Android 系统安装器完成升级，现有观看记录会保留。",
            confirmLabel = "安装更新",
            dismissLabel = "稍后",
            onConfirm = { requestInstall(current.release, current.apk) },
            onDismiss = updateViewModel::dismiss,
        )
        is UpdateUiState.Failed -> UpdatePrompt(
            title = if (current.release == null) "检查更新失败" else "更新没有完成",
            message = current.message,
            confirmLabel = "重试",
            dismissLabel = "关闭",
            onConfirm = {
                current.release?.let(updateViewModel::download) ?: updateViewModel.check(force = true)
            },
            onDismiss = updateViewModel::dismiss,
        )
    }
}

@Composable
private fun UpdatePrompt(
    title: String,
    message: String,
    confirmLabel: String? = null,
    dismissLabel: String? = null,
    progress: Int? = null,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(title, confirmLabel) {
        if (confirmLabel != null) {
            delay(120L)
            runCatching { confirmFocus.requestFocus() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    message,
                    color = Color(0xFFD5D8DE),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                progress?.let {
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(
                        progress = { it / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Gold,
                    )
                    Text("$it%", Modifier.padding(top = 7.dp), color = Muted)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                if (dismissLabel != null) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Panel),
                    ) { Text(dismissLabel) }
                    Spacer(Modifier.width(10.dp))
                }
                if (confirmLabel != null && onConfirm != null) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.focusRequester(confirmFocus),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = Ink,
                        ),
                    ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
                }
            }
        },
        dismissButton = {},
        containerColor = Color(0xFF171B24),
        titleContentColor = Color.White,
        textContentColor = Color.White,
    )
}

/**
 * Waits before the launch check so the TV can finish bringing up its network.
 * A check that runs first would fail, and the view model would then sit out the
 * retry interval for nothing.
 */
private const val UPDATE_CHECK_DELAY_MS = 5L * 60L * 1000L
