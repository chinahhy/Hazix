package tv.hdao.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Tracks
import java.util.Locale
import kotlin.math.abs

/** Playback speeds offered by the player settings panel, slowest first. */
internal val SPEED_STEPS = listOf(
    0.5f to "0.5×",
    0.75f to "0.75×",
    1f to "1.0×",
    1.25f to "1.25×",
    1.5f to "1.5×",
    2f to "2.0×",
)

internal fun speedLabel(speed: Float): String =
    SPEED_STEPS.firstOrNull { abs(it.first - speed) < 0.02f }?.second
        ?: String.format(Locale.US, "%.2f×", speed)

/**
 * One selectable subtitle or audio track.
 *
 * [group] is null for the "subtitles off" entry, which is always offered first
 * so the user can always turn subtitles off again.
 */
internal data class TrackOption(
    val group: Tracks.Group?,
    val trackIndex: Int,
    val label: String,
)

internal fun Tracks.textOptions(): List<TrackOption> = buildList {
    add(TrackOption(null, 0, "关闭字幕"))
    groups.forEach { group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEach
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            add(TrackOption(group, index, format.label ?: format.language ?: "字幕 ${size}"))
        }
    }
}

internal fun Tracks.audioOptions(): List<TrackOption> = buildList {
    groups.forEach { group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEach
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            add(TrackOption(group, index, format.label ?: format.language ?: "音轨 ${size + 1}"))
        }
    }
}

/** Index into [textOptions], or 0 (subtitles off) when nothing is selected. */
internal fun Tracks.selectedTextIndex(): Int {
    val options = textOptions()
    groups.forEach { group ->
        if (group.type != C.TRACK_TYPE_TEXT || !group.isSelected) return@forEach
        for (index in 0 until group.length) {
            if (!group.isTrackSelected(index)) continue
            val optionIndex = options.indexOfFirst { it.group === group && it.trackIndex == index }
            if (optionIndex >= 0) return optionIndex
        }
    }
    return 0
}

/** Index into [audioOptions], or -1 when nothing is reported as selected. */
internal fun Tracks.selectedAudioIndex(): Int {
    val options = audioOptions()
    groups.forEach { group ->
        if (group.type != C.TRACK_TYPE_AUDIO || !group.isSelected) return@forEach
        for (index in 0 until group.length) {
            if (!group.isTrackSelected(index)) continue
            val optionIndex = options.indexOfFirst { it.group === group && it.trackIndex == index }
            if (optionIndex >= 0) return optionIndex
        }
    }
    return -1
}

/**
 * Subtitle, audio track and speed panel shown over the player.
 *
 * It is driven purely by key events (see PlayerScreen) instead of holding
 * focusable children, so it cannot interfere with the player's own focus.
 */
@Composable
internal fun PlayerSettingsPanel(
    tracks: Tracks,
    focusedRow: Int,
    speed: Float,
    modifier: Modifier = Modifier,
) {
    val textOptions = tracks.textOptions()
    val audioOptions = tracks.audioOptions()
    val textIndex = tracks.selectedTextIndex()
    val audioIndex = tracks.selectedAudioIndex()
    val audioValue = when {
        audioOptions.isEmpty() -> "不可用"
        audioIndex < 0 -> audioOptions.first().label
        else -> audioOptions.getOrNull(audioIndex)?.label ?: audioOptions.first().label
    }

    Column(
        modifier.width(430.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF2181C24))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text("播放设置", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        SettingsRow("字幕", textOptions.getOrNull(textIndex)?.label ?: "关闭字幕", focusedRow == 0)
        SettingsRow("音轨", audioValue, focusedRow == 1)
        SettingsRow("倍速", speedLabel(speed), focusedRow == 2)
        Spacer(Modifier.height(12.dp))
        Text("上下选择 · 左右调整 · 返回关闭", color = Color(0xFF98A0AC), fontSize = 13.sp)
    }
}

@Composable
private fun SettingsRow(label: String, value: String, focused: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (focused) Color(0xFF2B3140) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (focused) "▸" else " ", color = Gold, fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = if (focused) Gold else Color(0xFFD5D8DE),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
