package it.officina.automaticfotoreport

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
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

    val photoCount = session.photos.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = {
                        Column {
                            Text(
                                when (step) {
                                    Step.CONNECT -> "Archivia le foto"
                                    Step.SHARES -> "Cartella condivisa"
                                    Step.FOLDER -> "Destinazione"
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                when (step) {
                                    Step.CONNECT -> "$photoCount foto pronte da archiviare"
                                    Step.SHARES -> settings.host
                                    Step.FOLDER -> "\\\\${settings.host}\\$currentShare" + (if (path.isEmpty()) "" else "\\$path")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna alla fotocamera")
                        }
                    }
                )
                StepIndicator(step.ordinal, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp))
                else Spacer(Modifier.height(8.dp))
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {

            errorMsg?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null); Spacer(Modifier.width(10.dp)); Text(it)
                    }
                }
            }

            when (step) {
                // ---------- 1. Connessione al PC ----------
                Step.CONNECT -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ElevatedCard(
                        onClick = { if (!busy) { showScan = true; startScan() } },
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onPrimary) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Cerca PC in rete", style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Trova i computer con cartelle condivise sulla Wi-Fi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    SectionCard("Computer") {
                        OutlinedTextField(
                            value = settings.host, onValueChange = { settings = settings.copy(host = it) },
                            label = { Text("Indirizzo IP del PC") }, placeholder = { Text("es. 192.168.1.20") },
                            leadingIcon = { Icon(Icons.Default.Computer, null) },
                            singleLine = true, shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SectionCard("Credenziali Windows") {
                        OutlinedTextField(
                            value = settings.user, onValueChange = { settings = settings.copy(user = it) },
                            label = { Text("Utente") }, leadingIcon = { Icon(Icons.Default.Person, null) },
                            singleLine = true, shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settings.password, onValueChange = { settings = settings.copy(password = it) },
                            label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                            singleLine = true, shape = MaterialTheme.shapes.medium,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settings.domain, onValueChange = { settings = settings.copy(domain = it) },
                            label = { Text("Dominio (facoltativo)") }, leadingIcon = { Icon(Icons.Default.Domain, null) },
                            singleLine = true, shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                .clickable { settings = settings.copy(rememberPassword = !settings.rememberPassword) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ricorda password", Modifier.weight(1f))
                            Switch(settings.rememberPassword, { settings = settings.copy(rememberPassword = it) })
                        }
                    }

                    Button(
                        enabled = !busy && settings.host.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        onClick = {
                            run {
                                repo.connect(settings)
                                settings.save(context)
                                shares = try { repo.listShares() } catch (e: Exception) { emptyList() }
                                step = Step.SHARES
                            }
                        }
                    ) {
                        Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text("Connetti")
                    }
                }

                // ---------- 2. Scelta cartella condivisa ----------
                Step.SHARES -> Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                    if (shares.isEmpty()) {
                        Text(
                            "Non riesco a leggere l'elenco delle condivisioni: scrivi il nome della cartella condivisa.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Text("Scegli dove archiviare", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    }
                    ListCard(Modifier.weight(1f, fill = false)) {
                        items(shares) { name ->
                            EntryRow(
                                icon = Icons.Default.FolderShared, iconTint = MaterialTheme.colorScheme.primary,
                                title = name, subtitle = null, enabled = !busy, chevron = true
                            ) { openShare(name) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualShare, onValueChange = { manualShare = it },
                            label = { Text("Nome condivisione") }, singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            enabled = !busy && manualShare.isNotBlank(),
                            onClick = { openShare(manualShare.trim()) },
                            modifier = Modifier.height(56.dp)
                        ) { Text("Apri") }
                    }
                }

                // ---------- 3. Scelta sottocartella + nome zip ----------
                Step.FOLDER -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            enabled = !busy,
                            onClick = {
                                if (path.isEmpty()) step = Step.SHARES
                                else { path = SmbRepository.parentOf(path); refresh() }
                            },
                            label = { Text("Indietro") },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, null, Modifier.size(18.dp)) }
                        )
                        Spacer(Modifier.weight(1f))
                        AssistChip(
                            enabled = !busy,
                            onClick = { showNewFolder = true },
                            label = { Text("Nuova cartella") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, Modifier.size(18.dp)) }
                        )
                    }
                    ListCard(Modifier.weight(1f)) {
                        if (entries.isEmpty() && !busy) {
                            item {
                                Column(
                                    Modifier.fillMaxWidth().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.FolderOpen, null, Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.outline)
                                    Text("Cartella vuota", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        items(entries) { e ->
                            val isZip = e.name.endsWith(".zip", true)
                            EntryRow(
                                icon = when {
                                    e.isDir -> Icons.Default.Folder
                                    isZip -> Icons.Default.FolderZip
                                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                iconTint = when {
                                    e.isDir -> BrandAmber
                                    isZip -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline
                                },
                                title = e.name,
                                subtitle = if (e.isDir) null else
                                    Formatter.formatShortFileSize(context, e.size) + " · " +
                                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(e.modified)),
                                enabled = !busy,
                                chevron = e.isDir
                            ) {
                                if (e.isDir) { path = SmbRepository.join(path, e.name); refresh() }
                                else overwriteAsk = e.name   // tocca un file per sovrascriverlo
                            }
                        }
                    }

                    // Pannello di salvataggio
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                            Text(
                                "Tocca un file per sovrascriverlo, oppure scrivi un nuovo nome",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = fileBase, onValueChange = { fileBase = it },
                                label = { Text("Nome file") }, singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                prefix = { Text("${datePrefix}_", color = MaterialTheme.colorScheme.primary) },
                                suffix = { Text(".zip") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            progress?.let {
                                LinearProgressIndicator(
                                    progress = { it },
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(CircleShape)
                                )
                            }
                            Button(
                                enabled = !busy && ZipUtil.sanitizeName(fileBase).isNotEmpty() && photoCount > 0,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
                                onClick = {
                                    val name = "${datePrefix}_${ZipUtil.sanitizeName(fileBase)}.zip"
                                    val exists = entries.any { !it.isDir && it.name.equals(name, ignoreCase = true) }
                                    if (exists) overwriteAsk = name
                                    else { pendingName = name; pendingOverwrite = false; askVoice = true }
                                }
                            ) {
                                Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp))
                                Text("Salva $photoCount foto qui")
                            }
                        }
                    }
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


private val stepLabels = listOf("Computer", "Condivisione", "Destinazione")

@Composable
private fun StepIndicator(current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stepLabels.forEachIndexed { i, label ->
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                        .background(
                            if (i <= current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
                Text(
                    label, style = MaterialTheme.typography.labelSmall,
                    color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ListCard(modifier: Modifier = Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = modifier.fillMaxWidth()
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 6.dp), content = content)
    }
}

@Composable
private fun EntryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String?,
    enabled: Boolean,
    chevron: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = iconTint) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}
