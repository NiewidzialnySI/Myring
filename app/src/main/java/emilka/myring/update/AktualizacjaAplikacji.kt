package emilka.myring.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import emilka.myring.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

/**
 * Autoaktualizacja z GitHub (`update.json` w repo + APK z Releases).
 *
 * 1) Pobiera manifest → porównuje [BuildConfig.VERSION_CODE]
 * 2) Pobiera APK do cache
 * 3) „Zaktualizuj” → Intent + FileProvider (nieznane źródła)
 */
object AktualizacjaAplikacji {

    const val DOMYSLNY_MANIFEST =
        "https://raw.githubusercontent.com/NiewidzialnySI/Myring/main/update.json"

    private const val PREFS = "aktualizacja_prefs"
    private const val K_URL = "manifest_url"
    private const val K_AUTO = "auto_sprawdzaj"
    private const val K_OSTATNIE = "ostatnie_sprawdzenie_ms"
    private const val K_POMINIETY = "pominiety_version_code"

    private const val MIN_ODSTEP_MS = 6L * 60L * 60L * 1000L // 6 h

    data class InfoWersji(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String
    )

    sealed class Stan {
        data object Bezczynny : Stan()
        data object Sprawdzanie : Stan()
        data class Dostepna(val info: InfoWersji) : Stan()
        data object Aktualna : Stan()
        data class Pobieranie(val postep01: Float, val info: InfoWersji) : Stan()
        data class GotowaDoInstalacji(val plik: File, val info: InfoWersji) : Stan()
        data class Blad(val komunikat: String) : Stan()
    }

    private val _stan = MutableStateFlow<Stan>(Stan.Bezczynny)
    val stan: StateFlow<Stan> = _stan.asStateFlow()

    fun manifestUrl(context: Context): String {
        val zPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(K_URL, null)?.trim().orEmpty()
        if (zPrefs.isNotBlank()) return zPrefs
        return BuildConfig.UPDATE_MANIFEST_URL.ifBlank { DOMYSLNY_MANIFEST }
    }

    fun autoSprawdzaj(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(K_AUTO, true)

    fun ustawAutoSprawdzaj(context: Context, wlacz: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(K_AUTO, wlacz)
        }
    }

    fun lokalnaWersja(): String =
        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    /** Sprawdza przy starcie najwyżej raz na [MIN_ODSTEP_MS]. */
    suspend fun sprawdzPrzyStarcie(context: Context) {
        if (!autoSprawdzaj(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ostatnie = prefs.getLong(K_OSTATNIE, 0L)
        if (System.currentTimeMillis() - ostatnie < MIN_ODSTEP_MS) return
        sprawdz(context, cichyGdyAktualna = true)
    }

    suspend fun sprawdz(
        context: Context,
        cichyGdyAktualna: Boolean = false
    ): Stan = withContext(Dispatchers.IO) {
        _stan.value = Stan.Sprawdzanie
        try {
            val json = pobierzTekst(manifestUrl(context))
            val info = parsujManifest(json)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putLong(K_OSTATNIE, System.currentTimeMillis())
            }
            val pominiety = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(K_POMINIETY, -1)
            val wynik = when {
                info.versionCode > BuildConfig.VERSION_CODE &&
                    info.versionCode != pominiety -> Stan.Dostepna(info)
                else -> Stan.Aktualna
            }
            if (cichyGdyAktualna && wynik is Stan.Aktualna) {
                _stan.value = Stan.Bezczynny
            } else {
                _stan.value = wynik
            }
            wynik
        } catch (e: Exception) {
            val msg = komunikatBledu(e, "sprawdzania aktualizacji")
            val blad = Stan.Blad(msg)
            if (!cichyGdyAktualna) _stan.value = blad
            else _stan.value = Stan.Bezczynny
            blad
        }
    }

    fun pominTeWersje(context: Context, versionCode: Int) {
        if (versionCode > 0) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putInt(K_POMINIETY, versionCode)
            }
        }
        _stan.value = Stan.Bezczynny
    }

    fun wycisz() {
        _stan.value = Stan.Bezczynny
    }

    suspend fun pobierz(context: Context, info: InfoWersji): Boolean =
        withContext(Dispatchers.IO) {
            try {
                _stan.value = Stan.Pobieranie(0.02f, info)
                val dir = File(context.cacheDir, "apk").apply { mkdirs() }
                val plik = File(dir, "myring-update.apk")
                if (plik.exists()) plik.delete()
                pobierzPlik(info.apkUrl, plik) { p ->
                    _stan.value = Stan.Pobieranie(p, info)
                }
                _stan.value = Stan.GotowaDoInstalacji(plik, info)
                true
            } catch (e: Exception) {
                _stan.value = Stan.Blad(komunikatBledu(e, "pobierania APK"))
                false
            }
        }

    fun czyMoznaInstalowac(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun intentUstawienNieznaneZrodla(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun zainstaluj(context: Context, plik: File) {
        if (!plik.exists() || plik.length() < 1024L) {
            _stan.value = Stan.Blad("Pobrany plik APK jest uszkodzony lub pusty — spróbuj ponownie")
            return
        }
        if (!czyMoznaInstalowac(context)) {
            _stan.value = Stan.Blad("Brak zgody na instalację z nieznanych źródeł")
            try {
                context.startActivity(intentUstawienNieznaneZrodla(context))
            } catch (_: Exception) { }
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                plik
            )
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )
                clipData = ClipData.newRawUri("apk", uri)
            }
            val handlery = context.packageManager.queryIntentActivities(
                view, PackageManager.MATCH_DEFAULT_ONLY
            )
            for (ri in handlery) {
                context.grantUriPermission(
                    ri.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val chooser = Intent.createChooser(view, "Zainstaluj aktualizację").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri("apk", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            _stan.value = Stan.Blad(
                e.message?.takeIf { it.isNotBlank() }
                    ?: "Nie udało się otworzyć instalatora APK"
            )
        }
    }

    /** Pobierz + zainstaluj w jednym ciągu (przycisk Zaktualizuj). */
    suspend fun zaktualizuj(context: Context, info: InfoWersji) {
        if (!czyMoznaInstalowac(context)) {
            _stan.value = Stan.Blad("Włącz instalację z nieznanych źródeł dla Moje Krążki")
            try {
                context.startActivity(intentUstawienNieznaneZrodla(context))
            } catch (_: Exception) { }
            return
        }
        val ok = pobierz(context, info)
        if (!ok) return
        val gotowa = _stan.value as? Stan.GotowaDoInstalacji ?: return
        zainstaluj(context, gotowa.plik)
    }

    fun komunikatBledu(e: Exception, kontekst: String): String = when (e) {
        is UnknownHostException -> "Brak sieci — sprawdź połączenie internetowe"
        is java.net.SocketTimeoutException -> "Przekroczono czas oczekiwania — spróbuj ponownie"
        else -> {
            val raw = e.message?.takeIf { it.isNotBlank() }
            when {
                raw == null -> "Błąd $kontekst"
                raw.contains("HTTP 404") -> "Nie znaleziono pliku aktualizacji na serwerze"
                raw.contains("HTTP") -> "Błąd serwera przy $kontekst ($raw)"
                raw.contains("za mały") || raw.contains("apkUrl") ->
                    "Pobrany APK wygląda na uszkodzony"
                else -> "Błąd $kontekst: $raw"
            }
        }
    }

    private fun parsujManifest(json: String): InfoWersji {
        val o = JSONObject(json)
        val code = o.getInt("versionCode")
        val name = o.optString("versionName", code.toString())
        val apk = o.getString("apkUrl").trim()
        require(apk.startsWith("http")) { "apkUrl musi być https" }
        val log = o.optString("changelog", "")
        return InfoWersji(code, name, apk, log)
    }

    private fun pobierzTekst(url: String): String {
        val conn = otworz(url)
        try {
            if (conn.responseCode !in 200..299) {
                error("HTTP ${conn.responseCode} przy manifeście")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun pobierzPlik(url: String, cel: File, onPostep: (Float) -> Unit) {
        val conn = otworz(url)
        try {
            if (conn.responseCode !in 200..299) {
                error("HTTP ${conn.responseCode} przy APK")
            }
            val total = conn.contentLengthLong.coerceAtLeast(0L)
            conn.inputStream.use { input ->
                FileOutputStream(cel).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        readTotal += n
                        if (total > 0L) {
                            onPostep((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        } else {
                            onPostep(0.5f)
                        }
                    }
                    out.flush()
                }
            }
            onPostep(1f)
            if (cel.length() < 1024L) error("Pobrany plik jest za mały — sprawdź apkUrl")
        } finally {
            conn.disconnect()
        }
    }

    private fun otworz(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, application/octet-stream, */*")
            setRequestProperty("User-Agent", "Myring-AutoUpdate/${BuildConfig.VERSION_NAME}")
        }
    }
}
