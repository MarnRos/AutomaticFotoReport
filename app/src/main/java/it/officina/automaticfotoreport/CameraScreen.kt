package it.officina.automaticfotoreport

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CameraScreen(
    session: PhotoSession,
    user: AppUser,
    onArchive: () -> Unit,
    onUsers: () -> Unit,
    onLogout: () -> Unit
) {
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
        PermissionScreen { permissionLauncher.launch(Manifest.permission.CAMERA) }
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
    var flash by remember { mutableStateOf(false) }

    // Miniatura dell'ultima foto
    val lastPhoto = session.photos.lastOrNull()
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(lastPhoto) { thumb = lastPhoto?.let { loadThumb(it) } }

    fun shoot() {
        if (capturing) return
        capturing = true
        flash = true
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

    LaunchedEffect(flash) { if (flash) { kotlinx.coroutines.delay(120); flash = false } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
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

        // Lampo bianco allo scatto
        AnimatedVisibility(flash, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.6f)))
        }

        // ---------- Barra superiore ----------
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Automatic Foto Report", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(user.name, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
            CounterPill(session.photos.size)
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, "Menu", tint = Color.White)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (user.isAdmin) {
                        DropdownMenuItem(
                            text = { Text("Gestione utenti") },
                            leadingIcon = { Icon(Icons.Filled.ManageAccounts, null) },
                            onClick = { menu = false; onUsers() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Esci") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                        onClick = { menu = false; onLogout() }
                    )
                }
            }
        }

        // ---------- Barra inferiore ----------
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .navigationBarsPadding()
                .padding(top = 40.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (session.photos.isEmpty()) "Inquadra il quadro elettrico e scatta"
                else "Scatta altre foto o archivia",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Miniatura ultima foto
                Box(Modifier.size(60.dp)) {
                    val t = thumb
                    if (t != null) {
                        Image(
                            t.asImageBitmap(), contentDescription = "Ultima foto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White.copy(alpha = 0.6f)) }
                    }
                }

                ShutterButton(enabled = !capturing, onClick = { shoot() })

                // Archivia
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                    val enabled = session.photos.isNotEmpty() && !capturing
                    FilledIconButton(
                        onClick = onArchive,
                        enabled = enabled,
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = BrandAmber, contentColor = Color(0xFF2B1A00),
                            disabledContainerColor = Color.White.copy(alpha = 0.12f),
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        )
                    ) { Icon(Icons.Filled.FolderZip, contentDescription = "Archivia") }
                }
            }
        }

        if (capturing) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White, strokeWidth = 3.dp)
        }
    }

    // ---------- Scelta dopo lo scatto ----------
    if (showChoice) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) },
            title = { Text("Foto ${session.photos.size} salvata", textAlign = TextAlign.Center) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    thumb?.let {
                        Image(
                            it.asImageBitmap(), null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp))
                        )
                    }
                    Button(
                        onClick = { showChoice = false },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Scatta un'altra foto")
                    }
                    FilledTonalButton(
                        onClick = { showChoice = false; onArchive() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.FolderZip, null); Spacer(Modifier.width(8.dp))
                        Text("Archivia ${session.photos.size} foto")
                    }
                    TextButton(
                        onClick = { session.removeLast(); showChoice = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Scarta questa foto")
                    }
                }
            },
            confirmButton = { }
        )
    }
}

@Composable
private fun CounterPill(count: Int) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("$count", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, label = "shutter")
    Box(
        Modifier
            .size(84.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.5f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Accesso alla fotocamera", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Serve il permesso per fotografare i quadri elettrici.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, modifier = Modifier.height(52.dp)) { Text("Concedi permesso") }
    }
}

private suspend fun loadThumb(file: File): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        rotateByExif(file, bmp)
    }.getOrNull()
}

private fun rotateByExif(file: File, bmp: Bitmap): Bitmap {
    val o = androidx.exifinterface.media.ExifInterface(file.absolutePath)
        .getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)
    val deg = when (o) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bmp
    }
    val m = android.graphics.Matrix().apply { postRotate(deg) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}
