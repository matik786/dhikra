package com.dhikra.app

/**
 * A nearby mosque reported by a real data source. Instances are never
 * fabricated: they are only returned by a [MosqueSource] provider.
 */
data class DetectedMosque(
    val sourceId: String,
    val name: String,
    val distanceMi: Double,
    val address: String?
)

/** A mosque's iqamah schedule. Times are minutes since midnight, local time. */
data class MosqueSchedule(
    val times: Map<String, Int>,
    val sourceLabel: String,
    val updatedAt: Long
)

/**
 * Pluggable source of nearby mosques and their iqamah schedules.
 *
 * v1 ships with NO registered providers, deliberately: there is currently no
 * clean, licensed mosque-iqamah API suitable for a Play Store app —
 * MasjidTimes exposes no public API, Mawaqit's endpoints are unofficial and
 * Europe-centric, and MasjidNow offers no developer API. The app therefore
 * falls back gracefully to manual mosque entry + calculated prayer times.
 *
 * A future provider implements this interface and registers itself in
 * [MosqueSources]; the UI, cache, and scheduler need no other changes.
 * Providers must never scrape a site against its terms of use.
 */
interface MosqueSource {
    val id: String
    val displayName: String

    /** Mosques near (lat, lng), sorted by distance. Empty when unavailable. */
    fun detectNearby(lat: Double, lng: Double): List<DetectedMosque>

    /** Today's iqamah schedule for a detected mosque, or null when unavailable. */
    fun fetchSchedule(mosque: DetectedMosque): MosqueSchedule?
}

object MosqueSources {
    /** v1: no licensed source is available, so this stays empty. */
    private val providers: List<MosqueSource> = emptyList()

    fun hasProviders(): Boolean = providers.isNotEmpty()

    fun detectNearby(lat: Double, lng: Double): List<DetectedMosque> =
        providers
            .flatMap { runCatching { it.detectNearby(lat, lng) }.getOrDefault(emptyList()) }
            .sortedBy { it.distanceMi }

    fun fetchSchedule(mosque: DetectedMosque): MosqueSchedule? {
        val src = providers.firstOrNull { it.id == mosque.sourceId } ?: return null
        return runCatching { src.fetchSchedule(mosque) }.getOrNull()
    }
}
