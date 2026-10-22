package it.officina.automaticfotoreport

import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtil {

    /** Caratteri non ammessi nei nomi file di Windows. */
    private val invalid = Regex("""[\\/:*?"<>|]""")

    fun sanitizeName(raw: String): String {
        var n = raw.trim().replace(invalid, "_")
        if (n.endsWith(".zip", ignoreCase = true)) n = n.dropLast(4)
        return n.trim().trimEnd('.')
    }

    /** Crea lo zip con le foto rinominate in sequenza (nome_001.jpg, nome_002.jpg, ...). */
    fun createZip(photos: List<File>, baseName: String, outFile: File): File {
        ZipOutputStream(outFile.outputStream().buffered()).use { zos ->
            zos.setLevel(Deflater.BEST_SPEED) // i JPEG sono già compressi
            photos.forEachIndexed { i, f ->
                val entryName = "%s_%03d.jpg".format(baseName, i + 1)
                zos.putNextEntry(ZipEntry(entryName).apply { time = f.lastModified() })
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return outFile
    }
}
