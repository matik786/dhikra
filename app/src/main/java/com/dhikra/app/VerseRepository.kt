package com.dhikra.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class VerseRepository(private val context: Context) {

    companion object {
        /**
         * Process-wide cache of the parsed rotation list. The ~3MB Quran
         * asset is parsed once; every later repository instance reuses it
         * instead of re-reading the asset on the calling thread.
         */
        @Volatile
        private var cachedVerses: List<Ayah>? = null
    }

    private val prefs = context.getSharedPreferences("ayah_prefs", Context.MODE_PRIVATE)

    val verses: List<Ayah>
        get() = cachedVerses ?: synchronized(VerseRepository) {
            cachedVerses ?: load().also { cachedVerses = it }
        }

    private fun key(surah: Int, ayah: Int): Long =
        (surah.toLong() shl 32) or (ayah.toLong() and 0xffffffffL)

    private fun load(): List<Ayah> {
        // Full offline corpus: compact objects {s,a,ar,tr,en}, 6,236 verses.
        val corpus = HashMap<Long, JSONObject>(6236)
        val quranText = context.assets.open("quran.json").bufferedReader().readText()
        val quranArr = JSONArray(quranText)
        for (i in 0 until quranArr.length()) {
            val o = quranArr.getJSONObject(i)
            corpus[key(o.getInt("s"), o.getInt("a"))] = o
        }

        // Surah names used to build references like "Surah Ibrahim 14:7".
        val names = ArrayList<String>(114)
        val namesArr = JSONArray(context.assets.open("surah_names.json").bufferedReader().readText())
        for (i in 0 until namesArr.length()) names.add(namesArr.getString(i))

        // The original 25 cards, preserved byte-identically.
        val legacy = HashMap<Long, Ayah>(25)
        val legacyArr = JSONArray(context.assets.open("legacy_verses.json").bufferedReader().readText())
        for (i in 0 until legacyArr.length()) {
            val o = legacyArr.getJSONObject(i)
            val ayah = Ayah(
                theme = o.getString("theme"),
                arabic = o.getString("arabic"),
                transliteration = o.getString("transliteration"),
                translation = o.getString("translation"),
                reference = o.getString("reference"),
                surah = o.getInt("surah"),
                ayah = o.getInt("ayah")
            )
            legacy[key(ayah.surah, ayah.ayah)] = ayah
        }

        // Rotation order: [{s,a,theme}]. Original 25 cards come first so a
        // persisted index keeps pointing at the same card as before.
        val rotationArr = JSONArray(context.assets.open("rotation.json").bufferedReader().readText())
        return List(rotationArr.length()) { i ->
            val e = rotationArr.getJSONObject(i)
            val s = e.getInt("s")
            val a = e.getInt("a")
            val k = key(s, a)
            legacy[k] ?: run {
                val q = corpus[k]
                    ?: throw IllegalStateException("Quran verse missing for $s:$a")
                val name = if (s in 1..names.size) names[s - 1] else "Surah $s"
                Ayah(
                    theme = e.getString("theme"),
                    arabic = q.getString("ar"),
                    transliteration = q.getString("tr"),
                    translation = q.getString("en"),
                    reference = "Surah $name $s:$a",
                    surah = s,
                    ayah = a
                )
            }
        }
    }

    fun index(): Int = if (verses.isEmpty()) 0 else prefs.getInt("index", 0) % verses.size

    fun current(): Ayah = verses[index()]

    fun next(): Ayah {
        val n = (index() + 1) % verses.size
        prefs.edit().putInt("index", n).apply()
        return verses[n]
    }
}
