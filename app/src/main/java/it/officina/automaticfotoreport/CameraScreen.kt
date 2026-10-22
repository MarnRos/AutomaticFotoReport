package it.officina.automaticfotoreport

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CameraScreen(session: PhotoSession, onArchive: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Serve il permesso di usare la fotocamera.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Concedi permesso")
            }
        }
        return
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(90)
            .build()
    }
    var capturing by remember { mutableStateOf(false) }
    var showChoice by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )

        // Contatore foto
        Text(
            text = "Foto: ${session.photos.size}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // Pulsante archivia (visibile se ci sono foto)
        if (session.photos.isNotEmpty()) {
            FilledTonalButton(
                onClick = onArchive,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Archive, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Archivia")
            }
        }

        // Pulsante di scatto
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .size(80.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .clickable(enabled = !capturing) {
                    capturing = true
                    val file = session.newPhotoFile()
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                session.add(file)
                                capturing = false
                                showChoice = true
                            }

                            override fun onError(exc: ImageCaptureException) {
                                capturing = false
                                Toast.makeText(context, "Errore scatto: ${exc.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
                .padding(8.dp)
                .clip(CircleShape)
                .background(if (capturing) Color.Gray else Color.White)
        )

        if (capturing) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
    }

    // Popup dopo lo scatto
    if (showChoice) {
        AlertDialog(
            onDismissRequest = { },   // l'utente deve scegliere
            title = { Text("Foto salvata (${session.photos.size})") },
            text = { Text("Vuoi scattare un'altra foto o archiviare le foto fatte?") },
            confirmButton = {
                Button(onClick = { showChoice = false; onArchive() }) { Text("Archivia") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { session.removeLast(); showChoice = false }) {
                        Text("Scarta")
                    }
                    TextButton(onClick = { showChoice = false }) { Text("Scatta altra") }
                }
            }
        )
    }
}
