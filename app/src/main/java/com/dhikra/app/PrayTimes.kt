package com.dhikra.app

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Prayer-time calculation (standard astronomical algorithm, same family as
 * praytimes.org). Pure Kotlin/JVM — no Android dependencies.
 */
object PrayTimes {

    data class CalcMethod(
        val name: String,
        val fajrAngle: Double,
        val ishaAngle: Double,
        val ishaMinutes: Int = 0
    )

    val METHODS = listOf(
        CalcMethod("Muslim World League", 18.0, 17.0),
        CalcMethod("ISNA (North America)", 15.0, 15.0),
        CalcMethod("Egyptian General Authority", 19.5, 17.5),
        CalcMethod("Umm al-Qura, Makkah", 18.5, 0.0, ishaMinutes = 90),
        CalcMethod("University of Karachi", 18.0, 18.0)
    )

    data class DayTimes(
        val fajr: Double,
        val sunrise: Double,
        val dhuhr: Double,
        val asr: Double,
        val maghrib: Double,
        val isha: Double
    ) {
        fun asMap(): Map<String, Double> = mapOf(
            "Fajr" to fajr,
            "Dhuhr" to dhuhr,
            "Asr" to asr,
            "Maghrib" to maghrib,
            "Isha" to isha
        )
    }

    private const val RISE_SET_ANGLE = 0.833

    fun getTimes(
        year: Int, month: Int, day: Int,
        lat: Double, lng: Double, tzHours: Double,
        method: CalcMethod, hanafiAsr: Boolean
    ): DayTimes {
        val jd = julianDay(year, month, day) - lng / (15.0 * 24.0)
        // initial guesses, refined by iteration
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0, 18.0)
        repeat(2) { t = computeTimes(t, jd, lat, method, hanafiAsr) }
        t = adjustTimes(t, tzHours, lng, method)
        return DayTimes(t[0], t[1], t[2], t[3], t[5], t[6])
    }

    /** Convenience: times for a [Calendar] day in its own timezone. */
    fun getTimes(
        cal: Calendar, lat: Double, lng: Double,
        method: CalcMethod, hanafiAsr: Boolean
    ): DayTimes {
        val tzHours = cal.timeZone.getOffset(cal.timeInMillis) / 3600000.0
        return getTimes(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            lat, lng, tzHours, method, hanafiAsr
        )
    }

    private fun computeTimes(
        times: DoubleArray, jd: Double, lat: Double,
        method: CalcMethod, hanafiAsr: Boolean
    ): DoubleArray {
        val p = times.map { it / 24.0 }
        val fajr = sunAngleTime(method.fajrAngle, p[0], jd, lat, ccw = true)
        val sunrise = sunAngleTime(RISE_SET_ANGLE, p[1], jd, lat, ccw = true)
        val dhuhr = midDay(p[2], jd)
        val asr = asrTime(if (hanafiAsr) 2 else 1, p[3], jd, lat)
        val sunset = sunAngleTime(RISE_SET_ANGLE, p[4], jd, lat, ccw = false)
        val maghrib = sunAngleTime(RISE_SET_ANGLE, p[5], jd, lat, ccw = false)
        val isha = sunAngleTime(method.ishaAngle, p[6], jd, lat, ccw = false)
        return doubleArrayOf(fajr, sunrise, dhuhr, asr, sunset, maghrib, isha)
    }

    private fun adjustTimes(
        times: DoubleArray, tzHours: Double, lng: Double, method: CalcMethod
    ): DoubleArray {
        val out = times.map { it + tzHours - lng / 15.0 }.toDoubleArray()
        if (method.ishaMinutes > 0) {
            out[6] = out[5] + method.ishaMinutes / 60.0 // isha = maghrib + minutes
        }
        return out.map { fixHour(it) }.toDoubleArray()
    }

    private fun midDay(time: Double, jd: Double): Double {
        val eqt = equationOfTime(jd + time)
        return fixHour(12.0 - eqt)
    }

    private fun sunAngleTime(angle: Double, time: Double, jd: Double, lat: Double, ccw: Boolean): Double {
        val decl = sunDeclination(jd + time)
        val noon = midDay(time, jd)
        val cosH = (-sinDeg(angle) - sinDeg(decl) * sinDeg(lat)) /
                (cosDeg(decl) * cosDeg(lat))
        val h = acosDeg(cosH.coerceIn(-1.0, 1.0)) / 15.0
        return noon + if (ccw) -h else h
    }

    private fun asrTime(factor: Int, time: Double, jd: Double, lat: Double): Double {
        val decl = sunDeclination(jd + time)
        val angle = -arccotDeg(factor + tanDeg(abs(lat - decl)))
        return sunAngleTime(angle, time, jd, lat, ccw = false)
    }

    private fun sunDeclination(jd: Double): Double = sunPosition(jd).first
    private fun equationOfTime(jd: Double): Double = sunPosition(jd).second

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g))
        val e = 23.439 - 0.00000036 * d
        val decl = asinDeg(sinDeg(e) * sinDeg(l))
        var ra = atan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15.0
        ra = fixHour(ra)
        val eqt = q / 15.0 - ra
        return decl to eqt
    }

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun fixAngle(a: Double): Double = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double): Double = a - 24.0 * floor(a / 24.0)

    private fun sinDeg(d: Double): Double = sin(Math.toRadians(d))
    private fun cosDeg(d: Double): Double = cos(Math.toRadians(d))
    private fun tanDeg(d: Double): Double = tan(Math.toRadians(d))
    private fun asinDeg(x: Double): Double = Math.toDegrees(asin(x))
    private fun acosDeg(x: Double): Double = Math.toDegrees(acos(x))
    private fun atan2Deg(y: Double, x: Double): Double = Math.toDegrees(atan2(y, x))
    private fun arccotDeg(x: Double): Double = Math.toDegrees(atan2(1.0, x))

    /** 6.25 -> "06:15" */
    fun format(hours: Double): String {
        var totalMin = floor(hours * 60.0 + 0.5).toInt()
        totalMin = ((totalMin % 1440) + 1440) % 1440
        return "%02d:%02d".format(totalMin / 60, totalMin % 60)
    }

    /** "06:15" -> 375 (minutes since midnight), null if blank/invalid. */
    fun parseMinutes(s: String): Int? {
        val t = s.trim()
        if (t.isEmpty()) return null
        val parts = t.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun minutesToString(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
}
