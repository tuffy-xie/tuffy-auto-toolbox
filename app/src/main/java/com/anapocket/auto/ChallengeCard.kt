package com.anapocket.auto

/**
 * 一张真实存在的挑战卡片（v10 队列粒度：日期 × 分类 × 卡片）。
 *
 * 身份用 `cat + title`：同一分类下标题不同即为不同任务，
 * 因此「徒歩で2km」和「徒歩で2km（別コース）」不会被错误合并；
 * 而 WebView 懒加载重复渲染的同名卡片会被 dedupe 掉。
 */
data class ChallengeCard(
    val cat: Cat,
    val title: String,
    val meters: Int?,   // null = UNKNOWN（无法可靠提取距离）
    val rawTitle: String
) {
    val key: String get() = "${cat.name}|${title.trim()}"

    val distanceLabel: String get() = meters?.let { Dist.label(it) } ?: "UNKNOWN"
}

data class CardSelection(
    val matched: List<ChallengeCard> = emptyList(),
    val overLimit: List<ChallengeCard> = emptyList(),
    val unknown: List<ChallengeCard> = emptyList()
) {
    val discovered: Int get() = matched.size + overLimit.size + unknown.size
}

object Cards {

    /**
     * 严格模式：标题必须**以分类名开头**，紧跟「で」+ 数字（或直接数字）。
     *
     * 真机上存在两类会污染数据的标题（2026-10-26 实测）：
     *   徒歩や自転車で合計2kmのエコ移動をしよう！   <- 复合环保任务，不是徒歩挑战
     *   【毎週月曜日更新】移動手段問わず1週間で500km移動しよう！ <- 周任务，不是当日挑战
     * 用 contains 判定会把它们算进来（前者甚至会被判成 BIKE），所以必须锚定开头。
     */
    private val strictRe = Regex(
        "^(移動手段問わず|自転車|徒歩|車、電車、モノレール|車|新幹線|飛行機)\\s*で?\\s*(\\d+(?:,\\d{3})*(?:\\.\\d+)?)\\s*(km|m)(?![a-z])"
    )

    /** 从一条可读文本构造卡片；不像「当日距离挑战」标题则返回 null。 */
    fun fromTitle(raw: String?): ChallengeCard? {
        if (raw.isNullOrBlank()) return null
        val t = Dist.normalize(raw).trim()
        if (t.length < 4) return null
        val m = strictRe.find(t)
        if (m == null) {
            // Observed under WALK with a same-day native deadline. Never accept weekly/compound titles.
            // 11/11: ANA prefixed the same card with an end-date tag, e.g. "【11/13終了】元気にお出かけ💪1日5km歩こう🚶".
            val dailyWalk = Regex("^(?:【[^】]{1,20}】\\s*)?元気にお出かけ.*?1日\\s*(\\d+(?:\\.\\d+)?)\\s*(km|m)\\s*歩こう").find(t) ?: return null
            val value = dailyWalk.groupValues[1].toDoubleOrNull() ?: return null
            val meters = Math.round(value * if (dailyWalk.groupValues[2] == "km") 1000.0 else 1.0).toInt()
            return ChallengeCard(Cat.WALK, t, meters.takeIf { it > 0 }, raw.trim())
        }
        val cat = when (m.groupValues[1]) {
            "移動手段問わず" -> Cat.MOV_ANY
            "自転車" -> Cat.BIKE
            "徒歩" -> Cat.WALK
            "新幹線" -> Cat.SHINKANSEN
            "飛行機" -> Cat.FLIGHT
            // R39 device evidence: this exact shared-mode title is listed under ANA's car icon.
            "車", "車、電車、モノレール" -> Cat.CAR
            else -> return null
        }
        // 距离直接取正则捕获组，避免同一串里其它数字干扰
        val v = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: return null
        val unit = m.groupValues[3]
        val meters = if (unit == "km") Math.round(v * 1000.0).toInt() else Math.round(v).toInt()
        if (meters <= 0) return ChallengeCard(cat, t, null, raw.trim())
        return ChallengeCard(cat, t, meters, raw.trim())
    }

    /** 按 (cat,title) 去重，保留首次出现顺序。 */
    fun dedupe(list: List<ChallengeCard>): List<ChallengeCard> {
        val seen = LinkedHashMap<String, ChallengeCard>()
        for (c in list) if (!seen.containsKey(c.key)) seen[c.key] = c
        return seen.values.toList()
    }

    /**
     * 距离筛选：meters <= maxMeters 入选。
     * 距离无法提取的单独归入 unknown，不猜测、不丢弃（便于人工核对）。
     */
    fun select(list: List<ChallengeCard>, cat: Cat, maxMeters: Int): CardSelection {
        val matched = ArrayList<ChallengeCard>()
        val over = ArrayList<ChallengeCard>()
        val unknown = ArrayList<ChallengeCard>()
        for (c in list) {
            if (c.cat != cat) continue
            val m = c.meters
            when {
                m == null -> unknown.add(c)
                m <= maxMeters -> matched.add(c)
                else -> over.add(c)
            }
        }
        // 按距离升序，便于先做小目标（更容易达成）
        matched.sortBy { it.meters ?: Int.MAX_VALUE }
        return CardSelection(matched, over, unknown)
    }
}
