package it.officina.automaticfotoreport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    val session: PhotoSession = viewModel()
                    var archiving by rememberSaveable { mutableStateOf(false) }

                    if (archiving) {
                        ArchiveScreen(
                            session = session,
                            onBack = { archiving = false },   // torna alla fotocamera, le foto restano
                            onDone = { archiving = false }    // sessione svuotata, si riparte
                        )
                    } else {
                        CameraScreen(session = session, onArchive = { archiving = true })
                    }
                }
            }
        }
    }
}
