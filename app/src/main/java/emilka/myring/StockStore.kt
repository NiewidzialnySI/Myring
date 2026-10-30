package emilka.myring

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Pojedyncze opakowanie krążków. */
data class RingPackage(
    val ean: String,
    val label: String,
    val total: Int,
    val used: Int
) {
    val remaining: Int get() = (total - used).coerceAtLeast(0)
}

/**
 * Zapas krążków w opakowaniach (EAN → liczba). Domyślnie przechowujemy listę opakowań
 * i ile krążków z każdego zużyto. Używane przez panel "zapas krążków" i pobieranie EAN.
 */
object StockStore {
    private const val PREFS = "myring_stock"
    private const val KEY_PACKAGES = "packages"
    private const val KEY_KNOWN = "known_eans"

    /** Tylko opakowania z pozostałymi krążkami (zużyte znika z listy). */
    fun load(context: Context): List<RingPackage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PACKAGES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RingPackage(
                    ean = o.getString("ean"),
                    label = o.optString("label", "Opakowanie $i"),
                    total = o.getInt("total"),
                    used = o.getInt("used")
                )
            }.filter { it.remaining > 0 }
        }.getOrDefault(emptyList())
    }

    /** Wszystkie opakowania (także zużyte — do wewnętrznych zapisów). */
    private fun loadAll(context: Context): List<RingPackage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PACKAGES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RingPackage(
                    ean = o.getString("ean"),
                    label = o.optString("label", "Opakowanie $i"),
                    total = o.getInt("total"),
                    used = o.getInt("used")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, list: List<RingPackage>) {
        val arr = JSONArray()
        list.forEach { p ->
            val o = JSONObject()
                .put("ean", p.ean)
                .put("label", p.label)
                .put("total", p.total)
                .put("used", p.used)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PACKAGES, arr.toString()).apply()
    }

    fun addPackage(context: Context, ean: String, label: String, total: Int) {
        val base = ean.substringBefore("#")
        val list = loadAll(context).toMutableList()
        // Dodajemy jako OSOBNĄ pozycję (nawet ten sam EAN), by dołożyć ilość,
        // a nie resetować istniejące opakowanie do pełnego stanu. EAN zapamiętujemy.
        list.add(RingPackage(ean = "$base#${list.count { it.ean.substringBefore("#") == base }}", label = label, total = total, used = 0))
        save(context, list)
        rememberEan(context, base, total)
    }

    /** Pamiętane EAN-y z ostatnią liczbą krążków w opakowaniu. */
    private fun knownCounts(context: Context): Map<String, Int> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_KNOWN, null) ?: return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                o.getString("ean") to o.optInt("total", 0)
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun rememberEan(context: Context, ean: String, total: Int) {
        val base = ean.substringBefore("#")
        val current = knownCounts(context).toMutableMap()
        current[base] = total
        val arr = JSONArray()
        current.forEach { (e, t) -> arr.put(JSONObject().put("ean", e).put("total", t)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_KNOWN, arr.toString()).apply()
    }

    /** Zużywa jeden krążek z danego opakowania (jeśli zostały). */
    fun useRing(context: Context, ean: String): Boolean {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.ean == ean }
        if (idx < 0) return false
        val p = list[idx]
        if (p.remaining <= 0) return false
        list[idx] = p.copy(used = p.used + 1)
        save(context, list)
        return true
    }

    /** Zużywa jeden krążek z dowolnego opakowania z danym kodem (głównym). */
    fun useRingByBase(context: Context, baseEan: String): Boolean {
        val base = baseEan.substringBefore("#")
        val list = loadAll(context).toMutableList()
        val idx = list.indexOfFirst { it.ean.substringBefore("#") == base && it.remaining > 0 }
        if (idx < 0) return false
        val p = list[idx]
        list[idx] = p.copy(used = p.used + 1)
        save(context, list)
        return true
    }

    /** Usuwa jedno opakowanie (po dokładnym wewnętrznym EAN z #sufiksem). EAN pozostaje zapamiętany. */
    fun removePackage(context: Context, internalEan: String): Boolean {
        val list = loadAll(context).toMutableList()
        val removed = list.removeAll { it.ean == internalEan }
        if (removed) save(context, list)
        return removed
    }

    /** Czy dany EAN był kiedykolwiek dodany (zapamiętany). */
    fun knowsEan(context: Context, ean: String): Boolean {
        val base = ean.substringBefore("#")
        return knownCounts(context).containsKey(base)
    }

    /** Ilość do podpowiedzi dla znanego EAN (z pamięci, nawet gdy opakowanie zużyte). */
    fun knownTotal(context: Context, ean: String): Int {
        val base = ean.substringBefore("#")
        return knownCounts(context)[base] ?: findByEan(context, ean)?.total ?: 0
    }

    /** Ostatnie opakowanie o danym EAN (z aktywnych). */
    fun findByEan(context: Context, ean: String): RingPackage? {
        val base = ean.substringBefore("#")
        return loadAll(context).lastOrNull { it.ean.substringBefore("#") == base }
    }

    /** Podstawa kodu EAN (bez unikalnego sufiksu pozycji). */
    fun baseEan(ean: String): String = ean.substringBefore("#")

    /** Łączna liczba pozostałych krążków we wszystkich opakowaniach. */
    fun totalRemaining(context: Context): Int =
        load(context).sumOf { it.remaining }
}