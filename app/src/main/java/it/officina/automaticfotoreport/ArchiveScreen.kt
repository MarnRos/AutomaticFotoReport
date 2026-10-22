package it.officina.automaticfotoreport

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }

    // Nome file: "AAAA-MM-GG_<nome>.zip" (la data di archiviazione viene aggiunta in automatico)
    val datePrefix = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(Date()) }
    var fileBase by remember { mutableStateOf("") }
    var overwriteAsk by remember { mutableStateOf<String?>(null) }      // file da confermare
    var pendingName by remember { mutableStateOf<String?>(null) }       // file scelto
    var pendingOverwrite by remember { mutableStateOf(false) }
    var askVoice by remember { mutableStateOf(false) }
    var recordVoice by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }

    // Ricerca PC in rete
    var showScan by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val foundPcs = remember { mutableStateListOf<FoundPc>() }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    fun startScan() {
        scanJob?.cancel()
        foundPcs.clear(); scanError = null; scanProgress = 0f; scanning = true
        scanJob = scope.launch {
            try {
                NetworkScanner.scan(
                    context,
                    onProgress = { d, t -> scanProgress = d.toFloat() / t },
                    onFound = { pc -> foundPcs.add(pc); foundPcs.sortBy { it.name ?: "~" + it.ip } }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                scanError = e.message
            } finally {
                scanning = false
            }
        }
    }
    var success by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true; errorMsg = null
            try { block() } catch (e: Exception) {
                errorMsg = e.message ?: e.javaClass.simpleName
            } finally { busy = false }
        }
    }

    fun refresh() = run { entries = repo.listEntries(path) }

    fun openShare(name: String) = run {
        repo.openShare(name)
        currentShare = name
        settings = settings.copy(lastShare = name)
        // riapre l'ultima cartella usata, se esiste ancora
        path = if (settings.lastPath.isNotEmpty() && repo.folderExists(settings.lastPath)) settings.lastPath else ""
        entries = repo.listEntries(path)
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
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { showScan = true; startScan() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Cerca PC in rete")
                    }
                    OutlinedTextField(
                        value = settings.host, onValueChange = { settings = settings.copy(host = it) },
                        label = { Text("PC (IP, es. 192.168.1.20)") }, singleLine = true,
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
                        if (entries.isEmpty() && !busy) {
                            item { Text("(cartella vuota)", Modifier.padding(16.dp)) }
                        }
                        items(entries) { e ->
                            val info: (@Composable () -> Unit)? = if (e.isDir) null else {
                                {
                                    Text(
                                        Formatter.formatShortFileSize(context, e.size) + " · " +
                                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(e.modified))
                                    )
                                }
                            }
                            ListItem(
                                headlineContent = { Text(e.name) },
                                supportingContent = info,
                                leadingContent = {
                                    Icon(
                                        when {
                                            e.isDir -> Icons.Default.Folder
                                            e.name.endsWith(".zip", true) -> Icons.Default.FolderZip
                                            else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                        }, null
                                    )
                                },
                                modifier = Modifier.clickable(enabled = !busy) {
                                    if (e.isDir) { path = SmbRepository.join(path, e.name); refresh() }
                                    else overwriteAsk = e.name   // tocca un file per sovrascriverlo
                                }
                            )
                            HorizontalDivider()
                        }
                    }

                    Text(
                        "Tocca un file per sovrascriverlo, oppure scrivi un nuovo nome:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = fileBase, onValueChange = { fileBase = it },
                        label = { Text("Nome file") }, singleLine = true,
                        prefix = { Text("${datePrefix}_") },
                        suffix = { Text(".zip") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    progress?.let {
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    Button(
                        enabled = !busy && ZipUtil.sanitizeName(fileBase).isNotEmpty() && session.photos.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        onClick = {
                            val name = "${datePrefix}_${ZipUtil.sanitizeName(fileBase)}.zip"
                            val exists = entries.any { !it.isDir && it.name.equals(name, ignoreCase = true) }
                            if (exists) overwriteAsk = name
                            else { pendingName = name; pendingOverwrite = false; askVoice = true }
                        }
                    ) { Text("Salva qui") }
                }
            }
        }
    }

    fun doSave(voice: File?) {
        val name = pendingName ?: return
        val overwrite = pendingOverwrite
        run {
            val remote = SmbRepository.join(path, name)
            if (!overwrite && repo.fileExists(remote)) error("Esiste già \"$name\" in questa cartella")
            val base = name.removeSuffix(".zip").removeSuffix(".ZIP")
            val tmp = File(context.cacheDir, "archivio.zip")
            try {
                withContext(Dispatchers.IO) { ZipUtil.createZip(session.photos.toList(), base, tmp, voice) }
                progress = 0f
                repo.upload(tmp, remote, overwrite) { p -> progress = p }
            } finally {
                tmp.delete(); progress = null
            }
            voice?.delete()
            settings = settings.copy(lastShare = currentShare, lastPath = path)
            settings.save(context)
            val n = session.photos.size
            session.clear()
            pendingName = null
            success = "$n foto" + (if (voice != null) " e messaggio vocale" else "") +
                " salvate in\n\\\\${settings.host}\\$currentShare\\$remote"
        }
    }

    overwriteAsk?.let { name ->
        AlertDialog(
            onDismissRequest = { overwriteAsk = null },
            title = { Text("Sovrascrivere il file?") },
            text = { Text("Il file \"$name\" verrà sostituito con il nuovo archivio. Il contenuto attuale andrà perso.") },
            confirmButton = {
                Button(
                    onClick = {
                        overwriteAsk = null
                        pendingName = name
                        pendingOverwrite = true
                        askVoice = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sovrascrivi") }
            },
            dismissButton = { TextButton(onClick = { overwriteAsk = null }) { Text("Annulla") } }
        )
    }

    if (askVoice) {
        AlertDialog(
            onDismissRequest = { askVoice = false },
            title = { Text("Messaggio vocale") },
            text = { Text("Vuoi aggiungere un messaggio vocale all'archivio?") },
            confirmButton = {
                Button(onClick = { askVoice = false; recordVoice = true }) {
                    Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("Sì, registra")
                }
            },
            dismissButton = {
                TextButton(onClick = { askVoice = false; doSave(null) }) { Text("No, salva") }
            }
        )
    }

    if (recordVoice) {
        VoiceMessageDialog(
            onConfirm = { file -> recordVoice = false; doSave(file) },
            onCancel = { recordVoice = false; askVoice = true }
        )
    }

    if (showScan) {
        AlertDialog(
            onDismissRequest = { scanJob?.cancel(); showScan = false },
            title = { Text("PC con cartelle condivise") },
            text = {
                Column {
                    if (scanning) {
                        LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth())
                        Text("Ricerca in corso… ${(scanProgress * 100).toInt()}%", Modifier.padding(vertical = 6.dp))
                    }
                    scanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!scanning && foundPcs.isEmpty() && scanError == null) {
                        Text("Nessun PC trovato. Verifica di essere sulla Wi-Fi aziendale e che sul PC la condivisione file sia attiva.")
                    }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(foundPcs) { pc ->
                            ListItem(
                                headlineContent = { Text(pc.name ?: "PC senza nome") },
                                supportingContent = { Text(pc.ip) },
                                leadingContent = { Icon(Icons.Default.Computer, null) },
                                modifier = Modifier.clickable {
                                    scanJob?.cancel()
                                    settings = settings.copy(host = pc.ip)
                                    showScan = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !scanning, onClick = { startScan() }) { Text("Ripeti") }
            },
            dismissButton = {
                TextButton(onClick = { scanJob?.cancel(); showScan = false }) { Text("Chiudi") }
            }
        )
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
                        entries = repo.listEntries(path)
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
