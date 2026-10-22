package it.officina.automaticfotoreport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(store: UserStore, onLogin: (AppUser) -> Unit) {
    var user by remember { mutableStateOf(store.rememberedUser() ?: "") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var showPwd by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun doLogin() {
        store.login(user, password)
            .onSuccess { u ->
                if (rememberMe) store.remember(u.name) else store.forget()
                onLogin(u)
            }
            .onFailure { error = it.message }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(BrandBlue, BrandBlueDark))
        )
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .systemBarsPadding().imePadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(46.dp)) }
            Spacer(Modifier.height(20.dp))
            Text("Automatic Foto Report", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(
                "Accedi per fotografare e archiviare i quadri",
                color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = user, onValueChange = { user = it; error = null },
                        label = { Text("Utente") }, leadingIcon = { Icon(Icons.Filled.Person, null) },
                        singleLine = true, shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it; error = null },
                        label = { Text("Password") }, leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { showPwd = !showPwd }) {
                                Icon(if (showPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Mostra password")
                            }
                        },
                        singleLine = true, shape = MaterialTheme.shapes.medium,
                        visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { doLogin() }),
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { rememberMe = !rememberMe },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ricorda accesso", Modifier.weight(1f))
                        Switch(rememberMe, { rememberMe = it })
                    }
                    Button(
                        onClick = { doLogin() },
                        enabled = user.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Accedi") }
                }
            }
        }
    }
}
