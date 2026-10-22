package it.officina.automaticfotoreport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File

/**
 * Registra un messaggio vocale in formato MP4 (audio AAC).
 * [onConfirm] riceve il file registrato, [onCancel] chiude senza messaggio.
 */
@Composable
fun VoiceMessageDialog(onConfirm: (File) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val outFile = remember { File(context.cacheDir, "messaggio_vocale.mp4").apply { delete() } }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.RECORD_AUDIO) }

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var recorded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val recording = recorder != null
    val playing = player != null

    LaunchedEffect(recording) {
        if (recording) {
            seconds = 0
            while (true) { delay(1000); seconds++ }
        }
    }

    fun stopPlayer() {
        player?.runCatching { stop(); release() }
        player = null
    }

    fun stopRecording() {
        recorder?.let { r ->
            runCatching { r.stop() }.onFailure { errorMsg = "Registrazione troppo breve"; outFile.delete() }
            r.release()
        }
        recorder = null
        recorded = outFile.exists() && outFile.length() > 0
    }

    fun startRecording() {
        stopPlayer()
        outFile.delete()
        errorMsg = null
        try {
            val r = newRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96_000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = r
            recorded = false
        } catch (e: Exception) {
            errorMsg = "Impossibile registrare: ${e.message}"
        }
    }

    fun play() {
        stopPlayer()
        player = MediaPlayer().apply {
            setDataSource(outFile.absolutePath)
            setOnCompletionListener { stopPlayer() }
            prepare()
            start()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.runCatching { stop() }
            recorder?.release()
            player?.release()
        }
    }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Messaggio vocale") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (!hasPermission) {
                    Text("Serve il permesso di usare il microfono.")
                    TextButton(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Concedi permesso")
                    }
                } else {
                    Text(
                        when {
                            recording -> "Registrazione in corso…"
                            recorded -> "Messaggio registrato"
                            else -> "Premi il microfono per iniziare"
                        }
                    )
                    Text(
                        "%d:%02d".format(seconds / 60, seconds % 60),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilledIconButton(
                            onClick = { if (recording) stopRecording() else startRecording() },
                            modifier = Modifier.size(64.dp),
                            colors = if (recording)
                                IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                            else IconButtonDefaults.filledIconButtonColors()
                        ) {
                            Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                        }
                        if (recorded && !recording) {
                            FilledTonalIconButton(
                                onClick = { if (playing) stopPlayer() else play() },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "Ascolta")
                            }
                        }
                    }
                    if (recorded && !recording) {
                        Text("Premi il microfono per registrare di nuovo", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(enabled = recorded && !recording, onClick = { stopPlayer(); onConfirm(outFile) }) {
                Text("Salva con messaggio")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (recording) stopRecording()
                stopPlayer(); outFile.delete(); onCancel()
            }) { Text("Annulla") }
        }
    )
}

@Suppress("DEPRECATION")
private fun newRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
