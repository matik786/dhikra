package com.dhikra.app

data class Ayah(
    val theme: String,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val reference: String,
    val surah: Int,
    val ayah: Int
) {
    val quranComUrl: String get() = "https://quran.com/$surah:$ayah"
}
