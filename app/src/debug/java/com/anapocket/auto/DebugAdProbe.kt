package com.anapocket.auto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Debug-only normal-process probe; no advertising reservation or ledger edits. */
object DebugAdProbe {
    private val busy = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun navigate(svc: AutoAccessibilityService) {
        if (AutoAccessibilityService.isRunning || !busy.compareAndSet(false, true)) return
        scope.launch {
            val id = "NAV" + System.currentTimeMillis()
            val overlay = OverlayKeeper(svc)
            val before = DailyAdStore.counters(svc, TokyoDate.of())
            RunForegroundService.start(svc, "广告入口核对 " + id)
            try {
                Logger.i("ADNAV", id + " START before=" + before)
                overlay.show(); delay(300L)
                val ok = DailyAdTaskController(svc).navigateOnly(id)
                val after = DailyAdStore.counters(svc, TokyoDate.of())
                Evidence.write(svc, "navigation_" + id + ".json", JSONObject().apply {
                    put("runId", id); put("ready", ok); put("before", before.toString()); put("after", after.toString())
                    put("ledgerUnchanged", before == after); put("realAdStarted", false)
                }.toString(2))
                Logger.i("ADNAV", id + " END ready=" + ok + " ledgerUnchanged=" + (before == after))
                dump(svc, id)
            } catch (t: Throwable) { Logger.e("ADNAV", id + " FAILED", t) }
            finally { overlay.hide(); RunForegroundService.stop(svc); busy.set(false) }
        }
    }
    fun dump(svc: AutoAccessibilityService, id: String = "DUMP" + System.currentTimeMillis()) {
        scope.launch {
            val ui = Ui(svc)
            val body = "activity=" + AutoAccessibilityService.foregroundActivity + "\npackage=" + ui.currentPackage() +
                "\nFOREGROUND=" + ui.foregroundNodes().map { ui.label(it) }.filter { it.isNotBlank() }.joinToString(" | ") +
                "\nNODES=" + ui.foregroundNodes().joinToString("\n") { n ->
                    val r = android.graphics.Rect(); n.getBoundsInScreen(r)
                    "class=" + n.className + " id=" + n.viewIdResourceName + " visible=" + n.isVisibleToUser + " enabled=" + n.isEnabled + " clickable=" + n.isClickable + " children=" + n.childCount + " bounds=" + r + " label=" + ui.label(n)
                } + "\nWINDOWS=" + ui.detailWindows().joinToString("\n") + "\n" + ui.dump()
            Evidence.write(svc, "ad_probe_" + id + ".txt", body)
            ui.captureVisual(includePng = true).png?.let { Evidence.writeBytes(svc, "ad_probe_" + id + ".png", it) }
            Logger.i("ADNAV", "snapshot=" + id)
        }
    }
}
