package it.officina.automaticfotoreport

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.EnumSet
import java.util.concurrent.TimeUnit

data class SmbSettings(
    val host: String = "",
    val domain: String = "",
    val user: String = "",
    val password: String = "",
    val rememberPassword: Boolean = true,
    val lastShare: String = "",
    val lastPath: String = ""
) {
    companion object {
        private const val P = "smb"
        fun load(ctx: Context): SmbSettings {
            val p = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            return SmbSettings(
                host = p.getString("host", "") ?: "",
                domain = p.getString("domain", "") ?: "",
                user = p.getString("user", "") ?: "",
                password = p.getString("password", "") ?: "",
                rememberPassword = p.getBoolean("remember", true),
                lastShare = p.getString("share", "") ?: "",
                lastPath = p.getString("path", "") ?: ""
            )
        }
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString("host", host).putString("domain", domain).putString("user", user)
            .putString("password", if (rememberPassword) password else "")
            .putBoolean("remember", rememberPassword)
            .putString("share", lastShare).putString("path", lastPath)
            .apply()
    }
}

/**
 * Accesso alle cartelle condivise di un PC Windows (protocollo SMB2/SMB3).
 * I percorsi dentro una condivisione usano il backslash, "" = radice.
 */
class SmbRepository {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    suspend fun connect(s: SmbSettings) = withContext(Dispatchers.IO) {
        disconnectInternal()
        val cfg = SmbConfig.builder()
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(60, TimeUnit.SECONDS)
            .build()
        val c = SMBClient(cfg)
        val conn = c.connect(s.host.trim())
        val auth = AuthenticationContext(s.user.trim(), s.password.toCharArray(), s.domain.trim())
        session = conn.authenticate(auth)
        client = c
        connection = conn
    }

    /** Elenco delle cartelle condivise del PC (esclude quelle amministrative con "$"). */
    suspend fun listShares(): List<String> = withContext(Dispatchers.IO) {
        val ses = session ?: error("Non connesso")
        val transport = SMBTransportFactories.SRVSVC.getTransport(ses)
        ServerService(transport).shares1
            .filter { (it.type and 0x0FFFFFFF) == 0 }  // STYPE_DISKTREE = cartella su disco
            .map { it.netName }
            .filter { !it.endsWith("$") }
            .sortedBy { it.lowercase() }
    }

    suspend fun openShare(name: String) = withContext(Dispatchers.IO) {
        val ses = session ?: error("Non connesso")
        share?.close()
        share = ses.connectShare(name) as DiskShare
    }

    suspend fun listDirs(path: String): List<String> = withContext(Dispatchers.IO) {
        val sh = share ?: error("Nessuna condivisione aperta")
        sh.list(path)
            .filter { it.fileName != "." && it.fileName != ".." }
            .filter { EnumWithValue.EnumUtils.isSet(it.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY) }
            .filter { !EnumWithValue.EnumUtils.isSet(it.fileAttributes, FileAttributes.FILE_ATTRIBUTE_HIDDEN) }
            .map { it.fileName }
            .sortedBy { it.lowercase() }
    }

    suspend fun folderExists(path: String): Boolean = withContext(Dispatchers.IO) {
        path.isEmpty() || (share?.folderExists(path) ?: false)
    }

    suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        (share ?: error("Nessuna condivisione aperta")).mkdir(path)
    }

    suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        share?.fileExists(path) ?: false
    }

    suspend fun upload(local: File, remotePath: String, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val sh = share ?: error("Nessuna condivisione aperta")
            val total = local.length().coerceAtLeast(1)
            sh.openFile(
                remotePath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_CREATE,          // fallisce se il file esiste già
                EnumSet.noneOf(SMB2CreateOptions::class.java)
            ).use { remote ->
                remote.outputStream.use { out ->
                    local.inputStream().use { input ->
                        val buf = ByteArray(256 * 1024)
                        var sent = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            sent += n
                            onProgress(sent.toFloat() / total)
                        }
                    }
                }
            }
        }

    suspend fun disconnect() = withContext(Dispatchers.IO) { disconnectInternal() }

    private fun disconnectInternal() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null; session = null; connection = null; client = null
    }

    companion object {
        fun join(parent: String, child: String) =
            if (parent.isEmpty()) child else "$parent\\$child"

        fun parentOf(path: String) = path.substringBeforeLast('\\', "")
    }
}
