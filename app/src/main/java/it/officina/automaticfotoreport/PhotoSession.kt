package it.officina.automaticfotoreport

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiene le foto della sessione corrente.
 * Le foto sono salvate su disco (memoria interna dell'app), quindi non si perdono
 * se l'app viene chiusa prima dell'archiviazione.
 */
class PhotoSession(app: Application) : AndroidViewModel(app) {

    private val dir = File(app.filesDir, "sessione").apply { mkdirs() }

    val photos = mutableStateListOf<File>().apply {
        addAll(dir.listFiles { f -> f.extension.equals("jpg", true) }
            ?.sortedBy { it.name }.orEmpty())
    }

    fun newPhotoFile(): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ITALY).format(Date())
        return File(dir, "IMG_$ts.jpg")
    }

    fun add(file: File) { photos.add(file) }

    fun removeLast() {
        photos.removeLastOrNull()?.delete()
    }

    /** Svuota la sessione dopo un'archiviazione riuscita. */
    fun clear() {
        photos.forEach { it.delete() }
        photos.clear()
    }
}
