package com.anapocket.auto

/**
 * 从挑战标题里提取移动距离。
 *
 * 真实标题样例（2026-10-26 真机）：
 *   徒歩で500m移動しよう！      -> 500 m
 *   徒歩で1km移動しよう！       -> 1000 m
 *   徒歩で5km移動しよう！       -> 5000 m
 *   移動手段問わず5km移動しよう！ -> 5000 m
 *   自転車で1km移動しよう！      -> 1000 m
 *
 * 要求（v10）：
 *  - 支持整数与小数（0.5km）
 *  - 支持全角数字与全角小数点
 *  - 支持 m / km / メートル / キロメートル
 *  - 无法可靠提取时返回 null（调用方记 UNKNOWN，不猜测）
 */
object Dist {

    data class Parsed(val meters: Int, val raw: String)

    private val fullWidth = charArrayOf(
        '０', '１', '２', '３', '４', '５', '６', '７', '８', '９', '．', 'ｋ', 'ｍ'
    )
    private val halfWidth = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', 'k', 'm'
    )

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val i = fullWidth.indexOf(ch)
            if (i >= 0) sb.append(halfWidth[i]) else sb.append(ch)
        }
        return sb.toString()
    }

    // 先匹配 km（含小数），再匹配 m；避免 "500m" 被当成 "500km"
    private val kmRe = Regex("(\\d+(?:,\\d{3})*(?:\\.\\d+)?)\\s*(?:km|キロメートル|キロ)")
    private val mRe = Regex("(\\d+(?:,\\d{3})*(?:\\.\\d+)?)\\s*(?:m|メートル)(?![a-z])")

    fun parse(rawTitle: String?): Parsed? {
        if (rawTitle.isNullOrBlank()) return null
        val t = normalize(rawTitle)

        val km = kmRe.find(t)
        if (km != null) {
            val v = km.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
            val m = Math.round(v * 1000.0).toInt()
            if (m <= 0) return null
            return Parsed(m, km.value.trim())
        }
        val mm = mRe.find(t)
        if (mm != null) {
            val v = mm.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
            val m = Math.round(v).toInt()
            if (m <= 0) return null
            return Parsed(m, mm.value.trim())
        }
        return null
    }

    /** 人类可读：500 -> "500m"，5000 -> "5km"，5500 -> "5.5km" */
    fun label(meters: Int): String =
        if (meters < 1000) "${meters}m"
        else if (meters % 1000 == 0) "${meters / 1000}km"
        else String.format(java.util.Locale.US, "%.1fkm", meters / 1000.0)
}
