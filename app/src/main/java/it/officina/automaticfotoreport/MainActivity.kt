package it.officina.automaticfotoreport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Screen { CAMERA, ARCHIVE, USERS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = UserStore(applicationContext)
        setContent {
            AppTheme {
                Surface {
                    val session: PhotoSession = viewModel()
                    var userName by rememberSaveable { mutableStateOf(store.autoLogin()?.name) }
                    var screen by rememberSaveable { mutableStateOf(Screen.CAMERA) }

                    val user = userName?.let { n -> store.users().firstOrNull { it.name == n && it.enabled } }

                    if (user == null) {
                        LoginScreen(store) { u -> userName = u.name; screen = Screen.CAMERA }
                    } else when (screen) {
                        Screen.CAMERA -> CameraScreen(
                            session = session,
                            user = user,
                            onArchive = { screen = Screen.ARCHIVE },
                            onUsers = { screen = Screen.USERS },
                            onLogout = { store.forget(); userName = null }
                        )
                        Screen.ARCHIVE -> ArchiveScreen(
                            session = session,
                            onBack = { screen = Screen.CAMERA },   // torna alla fotocamera, le foto restano
                            onDone = { screen = Screen.CAMERA }    // sessione svuotata, si riparte
                        )
                        Screen.USERS ->
                            if (user.isAdmin) UsersScreen(store) { screen = Screen.CAMERA }
                            else screen = Screen.CAMERA
                    }
                }
            }
        }
    }
}
