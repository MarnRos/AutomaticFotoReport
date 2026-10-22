package it.officina.automaticfotoreport

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/** PC trovato in rete con la condivisione file attiva (porta 445). */
data class FoundPc(val ip: String, val name: String?) {
    val label get() = if (name.isNullOrBlank()) ip else "$name  ($ip)"
}

/**
 * Cerca nella rete Wi-Fi i computer con cartelle condivise:
 * prova la porta SMB (445) su tutti gli indirizzi della sottorete
 * e ricava il nome del PC con una richiesta NetBIOS (o DNS inverso).
 */
object NetworkScanner {

    /** Indirizzo e lunghezza del prefisso della rete a cui è collegato il telefono. */
    fun localSubnet(ctx: Context): Pair<Inet4Address, Int>? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val la = lp.linkAddresses.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress
        } ?: return null
        return (la.address as Inet4Address) to la.prefixLength
    }

    /** Indirizzi da controllare (massimo 1024, cioè una rete /22). */
    private fun hostsOf(addr: Inet4Address, prefix: Int): List<String> {
        val p = prefix.coerceIn(22, 30)
        val ip = ByteBuffer.wrap(addr.address).int
        val mask = -1 shl (32 - p)
        val net = ip and mask
        val count = 1 shl (32 - p)
        return (1 until count - 1).map { net + it }.filter { it != ip }.map { v ->
            "${(v ushr 24) and 255}.${(v ushr 16) and 255}.${(v ushr 8) and 255}.${v and 255}"
        }
    }

    suspend fun scan(
        ctx: Context,
        onProgress: (done: Int, total: Int) -> Unit,
        onFound: (FoundPc) -> Unit
    ) {
        val (addr, prefix) = localSubnet(ctx)
            ?: throw IllegalStateException("Telefono non collegato a una rete Wi-Fi")
        val hosts = hostsOf(addr, prefix)
        val done = AtomicInteger(0)
        val limit = Semaphore(64)
        coroutineScope {
            for (h in hosts) {
                launch(Dispatchers.IO) {
                    limit.withPermit {
                        if (isSmbOpen(h)) {
                            val pc = FoundPc(h, netbiosName(h) ?: reverseDns(h))
                            withContext(Dispatchers.Main) { onFound(pc) }
                        }
                    }
                    val d = done.incrementAndGet()
                    withContext(Dispatchers.Main) { onProgress(d, hosts.size) }
                }
            }
        }
    }

    private fun isSmbOpen(ip: String): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, 445), 400); true }
    } catch (e: Exception) { false }

    /** Richiesta "node status" NetBIOS (UDP 137): restituisce il nome del computer. */
    private fun netbiosName(ip: String): String? = try {
        DatagramSocket().use { s ->
            s.soTimeout = 1000
            val q = ByteArray(50)
            q[0] = 0x4A; q[1] = 0x46          // id transazione
            q[5] = 1                           // 1 domanda
            q[12] = 0x20                       // lunghezza nome codificato
            q[13] = 'C'.code.toByte(); q[14] = 'K'.code.toByte()  // "*"
            for (i in 15 until 45) q[i] = 'A'.code.toByte()       // padding
            q[47] = 0x21                       // tipo NBSTAT
            q[49] = 1                          // classe IN
            s.send(DatagramPacket(q, q.size, InetAddress.getByName(ip), 137))

            val buf = ByteArray(1024)
            val p = DatagramPacket(buf, buf.size)
            s.receive(p)
            var off = 56                       // header + nome + tipo/classe + ttl + lunghezza
            if (p.length <= off) return null
            val n = buf[off].toInt() and 0xFF
            off++
            for (i in 0 until n) {
                val e = off + i * 18
                if (e + 18 > p.length) break
                val name = String(buf, e, 15, Charsets.US_ASCII).trim()
                val suffix = buf[e + 15].toInt() and 0xFF
                val group = (buf[e + 16].toInt() and 0x80) != 0
                if (suffix == 0 && !group && name.isNotEmpty()) return name
            }
            null
        }
    } catch (e: Exception) { null }

    private fun reverseDns(ip: String): String? = try {
        InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }?.substringBefore('.')
    } catch (e: Exception) { null }
}
