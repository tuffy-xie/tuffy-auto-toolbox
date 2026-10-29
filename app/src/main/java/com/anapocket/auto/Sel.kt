package com.anapocket.auto

/**
 * ANA Pocket 真实 UI 文案候选集。
 * 说明：日文为主，英文为广告 SDK 兜底；所有匹配均为包含匹配（忽略大小写）。
 */
object Sel {
    val BOTTOM_EARN = listOf("ためる")
    val BOTTOM_HOME = listOf("ホーム")

    val TAB_ADS = listOf("広告視聴")
    val TAB_CHALLENGE = listOf("チャレンジ")
    val TAB_SURVEY = listOf("アンケート")
    val TAB_BONUS = listOf("ボーナス")

    // 注意：不能包含裸 "視聴"，否则会命中顶部标签 "広告視聴"
    val WATCH_AD = listOf(
        "視聴する", "広告を見る", "動画を見る", "広告を視聴", "動画を再生",
        "Watch Ad", "Watch Video", "View Ad", "Play Ad"
    )

    val CLAIM = listOf(
        "受け取る", "報酬を受け取る", "ポイントを受け取る", "受け取り", "獲得",
        "Claim", "Get Reward", "Collect"
    )

    val CLOSE = listOf("閉じる", "広告を閉じる", "Close", "×", "✕", "✖")
    val SKIP = listOf("スキップ", "Skip Ad", "Skip", "広告をスキップ")
    val NEXT = listOf("次へ", "続ける", "Next", "Continue")
    val OK = listOf("OK", "はい", "許可", "Allow", "同意")
    val JOIN = listOf("参加する", "参加", "エントリー", "応募する")

    val POINT_LABEL = listOf("現在の所有ポイント", "所有ポイント")
    val AD_HINT = listOf("広告", "Ad", "動画")
}
