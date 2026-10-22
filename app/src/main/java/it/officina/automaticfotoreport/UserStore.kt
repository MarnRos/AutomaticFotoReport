package it.officina.automaticfotoreport

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class AppUser(val name: String, val enabled: Boolean, val isAdmin: Boolean)

/**
 * Utenti dell'app salvati sul telefono. Le password non sono salvate in chiaro:
 * si conserva solo un'impronta (PBKDF2 con sale casuale).
 * Al primo avvio viene creato l'utente "admin" con password "automatic".
 */
class UserStore(context: Context) {

    private val prefs = context.getSharedPreferences("users", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY)) {
            save(JSONObject().put(ADMIN, record(DEFAULT_ADMIN_PASSWORD, true)))
        }
    }

    private fun load() = JSONObject(prefs.getString(KEY, "{}")!!)
    private fun save(o: JSONObject) { prefs.edit().putString(KEY, o.toString()).apply() }

    fun users(): List<AppUser> {
        val o = load()
        return o.keys().asSequence().map { n ->
            AppUser(n, o.getJSONObject(n).optBoolean("enabled", true), n == ADMIN)
        }.sortedWith(compareBy({ !it.isAdmin }, { it.name.lowercase() })).toList()
    }

    /** Restituisce l'utente se nome e password sono corretti e l'utente è abilitato. */
    fun login(name: String, password: String): Result<AppUser> {
        val key = findKey(name) ?: return Result.failure(Exception("Utente o password errati"))
        val r = load().getJSONObject(key)
        if (!verify(password, r)) return Result.failure(Exception("Utente o password errati"))
        if (!r.optBoolean("enabled", true)) return Result.failure(Exception("Utente non abilitato. Contatta l'amministratore."))
        return Result.success(AppUser(key, true, key == ADMIN))
    }

    fun addUser(name: String, password: String) {
        val n = name.trim()
        require(n.isNotEmpty()) { "Inserisci il nome utente" }
        require(findKey(n) == null) { "L'utente \"$n\" esiste già" }
        checkPassword(password)
        save(load().put(n, record(password, true)))
    }

    fun setPassword(name: String, password: String) {
        checkPassword(password)
        val o = load()
        val enabled = o.getJSONObject(name).optBoolean("enabled", true)
        save(o.put(name, record(password, enabled)))
    }

    fun setEnabled(name: String, enabled: Boolean) {
        if (name == ADMIN) return
        val o = load()
        save(o.put(name, o.getJSONObject(name).put("enabled", enabled)))
    }

    fun delete(name: String) {
        if (name == ADMIN) return
        val o = load(); o.remove(name); save(o)
        if (rememberedUser() == name) forget()
    }

    // ---------- "Ricorda accesso" ----------
    // Si salva il nome utente e un'impronta legata alla password attuale:
    // se la password viene cambiata o l'utente disabilitato, l'accesso automatico decade.

    fun remember(name: String) {
        val r = load().optJSONObject(name) ?: return
        prefs.edit().putString("rem_user", name).putString("rem_tok", token(r)).apply()
    }

    fun forget() { prefs.edit().remove("rem_user").remove("rem_tok").apply() }

    fun rememberedUser(): String? = prefs.getString("rem_user", null)

    fun autoLogin(): AppUser? {
        val name = rememberedUser() ?: return null
        val r = load().optJSONObject(name) ?: return null
        if (!r.optBoolean("enabled", true) || prefs.getString("rem_tok", null) != token(r)) return null
        return AppUser(name, true, name == ADMIN)
    }

    // ---------- interni ----------

    private fun findKey(name: String): String? =
        load().keys().asSequence().firstOrNull { it.equals(name.trim(), ignoreCase = true) }

    private fun checkPassword(p: String) = require(p.length >= 4) { "La password deve avere almeno 4 caratteri" }

    private fun record(password: String, enabled: Boolean): JSONObject {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return JSONObject()
            .put("salt", b64(salt))
            .put("hash", b64(hash(password, salt)))
            .put("enabled", enabled)
    }

    private fun verify(password: String, r: JSONObject): Boolean {
        val salt = Base64.decode(r.getString("salt"), Base64.NO_WRAP)
        val expected = Base64.decode(r.getString("hash"), Base64.NO_WRAP)
        return MessageDigest.isEqual(hash(password, salt), expected)
    }

    private fun token(r: JSONObject) =
        b64(MessageDigest.getInstance("SHA-256").digest((r.getString("salt") + r.getString("hash")).toByteArray()))

    private fun hash(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 20_000, 256)).encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    companion object {
        const val ADMIN = "admin"
        private const val DEFAULT_ADMIN_PASSWORD = "automatic"
        private const val KEY = "list"
    }
}
