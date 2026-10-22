package it.officina.automaticfotoreport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Pagina di configurazione utenti, accessibile solo all'amministratore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(store: UserStore, onBack: () -> Unit) {
    var users by remember { mutableStateOf(store.users()) }
    var showAdd by remember { mutableStateOf(false) }
    var pwdFor by remember { mutableStateOf<String?>(null) }
    var deleteAsk by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun reload() { users = store.users() }
    fun toast(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text("Utenti", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Chi può usare l'app su questo telefono",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.PersonAdd, null) },
                text = { Text("Nuovo utente") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users, key = { it.name }) { u ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val tint = if (u.isAdmin) BrandAmber else MaterialTheme.colorScheme.primary
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (u.isAdmin) Icons.Filled.AdminPanelSettings else Icons.Filled.Person, null, tint = tint)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    u.isAdmin -> "Amministratore · accesso completo"
                                    u.enabled -> "Abilitato"
                                    else -> "Disabilitato"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!u.enabled) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { pwdFor = u.name }) { Icon(Icons.Filled.Key, "Cambia password") }
                        if (!u.isAdmin) {
                            IconButton(onClick = { deleteAsk = u.name }) {
                                Icon(Icons.Filled.Delete, "Elimina", tint = MaterialTheme.colorScheme.error)
                            }
                            Switch(
                                checked = u.enabled,
                                onCheckedChange = { store.setEnabled(u.name, it); reload() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        UserFormDialog(
            title = "Nuovo utente",
            askName = true,
            onDismiss = { showAdd = false },
            onSave = { name, pwd ->
                runCatching { store.addUser(name, pwd) }
                    .onSuccess { showAdd = false; reload(); toast("Utente \"${name.trim()}\" creato") }
                    .exceptionOrNull()?.message
            }
        )
    }

    pwdFor?.let { name ->
        UserFormDialog(
            title = "Nuova password per $name",
            askName = false,
            onDismiss = { pwdFor = null },
            onSave = { _, pwd ->
                runCatching { store.setPassword(name, pwd) }
                    .onSuccess { pwdFor = null; reload(); toast("Password di \"$name\" aggiornata") }
                    .exceptionOrNull()?.message
            }
        )
    }

    deleteAsk?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteAsk = null },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("Eliminare \"$name\"?") },
            text = { Text("L'utente non potrà più accedere all'app.") },
            confirmButton = {
                Button(
                    onClick = { store.delete(name); deleteAsk = null; reload() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { deleteAsk = null }) { Text("Annulla") } }
        )
    }
}

/** Dialogo per creare un utente o cambiare password. [onSave] restituisce un errore o null. */
@Composable
private fun UserFormDialog(
    title: String,
    askName: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, password: String) -> String?
) {
    var name by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (askName) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; error = null },
                        label = { Text("Nome utente") }, singleLine = true,
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = pwd, onValueChange = { pwd = it; error = null },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pwd2, onValueChange = { pwd2 = it; error = null },
                    label = { Text("Ripeti password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = pwd2.isNotEmpty() && pwd2 != pwd,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                error = if (pwd != pwd2) "Le password non coincidono" else onSave(name, pwd)
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
