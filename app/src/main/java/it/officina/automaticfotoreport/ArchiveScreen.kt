package it.officina.automaticfotoreport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Step { CONNECT, SHARES, FOLDER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(session: PhotoSession, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SmbRepository() }
    DisposableEffect(Unit) { onDispose { CoroutineScope(Dispatchers.IO).launch { repo.disconnect() } } }

    var settings by remember { mutableStateOf(SmbSettings.load(context)) }
    var step by remember { mutableStateOf(Step.CONNECT) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var shares by remember { mutableStateOf<List<String>>(emptyList()) }
    var manualShare by remember { mutableStateOf(settings.lastShare) }
    var currentShare by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }

    var zipName by remember {
        mutableStateOf("Quadro_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALY).format(Date()))
    }
    var progress by remember { mutableStateOf<Float?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true; errorMsg = null
            try { block() } catch (e: Exception) {
                errorMsg = e.message ?: e.javaClass.simpleName
            } finally { busy = false }
        }
    }

    fun refresh() = run { folders = repo.listDirs(path) }

    fun openShare(name: String) = run {
        repo.openShare(name)
        currentShare = name
        settings = settings.copy(lastShare = name)
        // riapre l'ultima cartella usata, se esiste ancora
        path = if (settings.lastPath.isNotEmpty() && repo.folderExists(settings.lastPath)) settings.lastPath else ""
        folders = repo.listDirs(path)
        step = Step.FOLDER
    }

    BackHandler {
        when (step) {
            Step.FOLDER -> if (path.isNotEmpty()) { path = SmbRepository.parentOf(path); refresh() } else step = Step.SHARES
            Step.SHARES -> step = Step.CONNECT
            Step.CONNECT -> onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            Step.CONNECT -> "Archivia ${session.photos.size} foto"
                            Step.SHARES -> "Cartelle condivise su ${settings.host}"
                            Step.FOLDER -> "\\\\${settings.host}\\$currentShare" + (if (path.isEmpty()) "" else "\\$path")
                        },
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna alla fotocamera")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
            errorMsg?.let {
                Text("Errore: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }

            when (step) {
                // ---------- 1. Connessione al PC ----------
                Step.CONNECT -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.host, onValueChange = { settings = settings.copy(host = it) },
                        label = { Text("PC (nome o IP, es. 192.168.1.20)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.domain, onValueChange = { settings = settings.copy(domain = it) },
                        label = { Text("Dominio (facoltativo)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.user, onValueChange = { settings = settings.copy(user = it) },
                        label = { Text("Utente") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.password, onValueChange = { settings = settings.copy(password = it) },
                        label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(settings.rememberPassword, { settings = settings.copy(rememberPassword = it) })
                        Text("Ricorda password")
                    }
                    Button(
                        enabled = !busy && settings.host.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            run {
                                repo.connect(settings)
                                settings.save(context)
                                shares = try { repo.listShares() } catch (e: Exception) { emptyList() }
                                step = Step.SHARES
                            }
                        }
                    ) { Text("Connetti") }
                }

                // ---------- 2. Scelta cartella condivisa ----------
                Step.SHARES -> Column(Modifier.fillMaxSize()) {
                    if (shares.isEmpty()) {
                        Text("Impossibile leggere l'elenco delle condivisioni. Scrivi il nome della cartella condivisa:")
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(shares) { name ->
                            ListItem(
                                headlineContent = { Text(name) },
                                leadingContent = { Icon(Icons.Default.FolderShared, null) },
                                modifier = Modifier.clickable(enabled = !busy) { openShare(name) }
                            )
                            HorizontalDivider()
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualShare, onValueChange = { manualShare = it },
                            label = { Text("Nome condivisione") }, singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = !busy && manualShare.isNotBlank(), onClick = { openShare(manualShare.trim()) }) {
                            Text("Apri")
                        }
                    }
                }

                // ---------- 3. Scelta sottocartella + nome zip ----------
                Step.FOLDER -> Column(Modifier.fillMaxSize()) {
                    Row {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                if (path.isEmpty()) step = Step.SHARES
                                else { path = SmbRepository.parentOf(path); refresh() }
                            }
                        ) {
                            Icon(Icons.Default.ArrowUpward, null); Spacer(Modifier.width(4.dp)); Text("Su")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(enabled = !busy, onClick = { showNewFolder = true }) {
                            Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(4.dp)); Text("Nuova cartella")
                        }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        if (folders.isEmpty() && !busy) {
                            item { Text("(nessuna sottocartella)", Modifier.padding(16.dp)) }
                        }
                        items(folders) { name ->
                            ListItem(
                                headlineContent = { Text(name) },
                                leadingContent = { Icon(Icons.Default.Folder, null) },
                                modifier = Modifier.clickable(enabled = !busy) {
                                    path = SmbRepository.join(path, name); refresh()
                                }
                            )
                            HorizontalDivider()
                        }
                    }

                    OutlinedTextField(
                        value = zipName, onValueChange = { zipName = it },
                        label = { Text("Nome archivio") }, singleLine = true,
                        suffix = { Text(".zip") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    progress?.let {
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    Button(
                        enabled = !busy && ZipUtil.sanitizeName(zipName).isNotEmpty() && session.photos.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        onClick = {
                            run {
                                val base = ZipUtil.sanitizeName(zipName)
                                val remote = SmbRepository.join(path, "$base.zip")
                                if (repo.fileExists(remote)) error("Esiste già un archivio \"$base.zip\" in questa cartella")
                                val tmp = File(context.cacheDir, "$base.zip")
                                try {
                                    withContext(Dispatchers.IO) { ZipUtil.createZip(session.photos.toList(), base, tmp) }
                                    progress = 0f
                                    repo.upload(tmp, remote) { p -> progress = p }
                                } finally {
                                    tmp.delete(); progress = null
                                }
                                settings = settings.copy(lastShare = currentShare, lastPath = path)
                                settings.save(context)
                                val n = session.photos.size
                                session.clear()
                                success = "$n foto salvate in\n\\\\${settings.host}\\$currentShare\\$remote"
                            }
                        }
                    ) { Text("Salva qui") }
                }
            }
        }
    }

    if (showNewFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("Nuova cartella") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nome") })
            },
            confirmButton = {
                Button(enabled = ZipUtil.sanitizeName(name).isNotEmpty(), onClick = {
                    showNewFolder = false
                    run {
                        val newPath = SmbRepository.join(path, ZipUtil.sanitizeName(name))
                        repo.mkdir(newPath)
                        path = newPath
                        folders = repo.listDirs(path)
                    }
                }) { Text("Crea") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("Annulla") } }
        )
    }

    success?.let { msg ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Archivio salvato") },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { success = null; onDone() }) { Text("Nuova sessione") } }
        )
    }
}
