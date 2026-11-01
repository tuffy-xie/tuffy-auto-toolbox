package com.anapocket.auto

/** Shared observed-node close driver; daily ads and challenge ads retain separate ledgers. */
class AdUiClose(private val svc: AutoAccessibilityService) {
    private val ui = Ui(svc)
    private val pkg = "us.ana_pocket.ANAPocket"
    private fun closeNode() = ui.foregroundNodes().firstOrNull {
        it.isVisibleToUser && it.isEnabled && AdSignals.closeLabel(ui.label(it))
    }
    var observedStoreOverlay = false
    var observedTapOnly = false
    private var seenStoreSheet = false
    private var storeBacks = 0
    private var playbackContinueRequests=0
    private var pangleReplay = PangleStoreReplayPolicy()
    private var chromeReturn=PangleChromeReturnPolicy()
    private var flowOwner:UiOwnership.Token?=null
    private var flowRun=""
    private var flowDate=""
    val returnedViaPangleLauncher: Boolean get() = pangleReplay.returnedViaLauncher
    private var opaqueSince = 0L
    private var revealCount = 0
    private var lastReveal = -100_000L
    private var revealedPng: ByteArray? = null
    private var revealedPngAt = 0L
    private var canvasControlBounds: android.graphics.Rect? = null
    private var canvasControlAt = 0L
    private var canvasControlProof = ""
    val recentlyRevealed: Boolean get() = android.os.SystemClock.elapsedRealtime() - lastReveal < 5000L
    fun beginFlow(knownStoreSheet: Boolean = false) {
        seenStoreSheet = knownStoreSheet; storeBacks = 0; opaqueSince = 0; revealCount = 0; playbackContinueRequests=0
        pangleReplay = PangleStoreReplayPolicy()
        chromeReturn=PangleChromeReturnPolicy();flowOwner=CheckinRuntime.ownership.owner()
        flowRun=AutoAccessibilityService.currentRunId;flowDate=TokyoDate.of()
        lastReveal = -100_000L; revealedPng = null; canvasControlBounds = null; canvasControlProof = ""
    }
    fun isOpaquePlayable(): Boolean {
        if (ui.currentPackage() != pkg) return false
        val n = ui.foregroundNodes().filter { it.isVisibleToUser }
        return n.any { it.viewIdResourceName == "playable" } && n.any { it.viewIdResourceName == "application-canvas" }
    }
    /** One tap reveals hidden player controls. It never creates a completion/reward receipt. */
    suspend fun revealIfNeeded(): Boolean {
        if (AutoAccessibilityService.stopRequested || !isOpaquePlayable()) return false
        val now = android.os.SystemClock.elapsedRealtime()
        if (opaqueSince == 0L) { opaqueSince = now; return false }
        if (!ControlRevealPolicy.canReveal(now - opaqueSince, revealCount, now - lastReveal, false)) return false
        val canvas = ui.foregroundNodes().firstOrNull { it.isVisibleToUser && it.viewIdResourceName == "application-canvas" } ?: return false
        val r = android.graphics.Rect(); canvas.getBoundsInScreen(r)
        if (r.width() < svc.resources.displayMetrics.widthPixels * .8 || r.height() < svc.resources.displayMetrics.heightPixels * .8) return false
        // A single touch in the actual player/canvas centre, away from the observed bottom install CTA.
        val x = r.centerX(); val y = r.centerY()
        if (ui.foregroundNodes().any {
            val b = android.graphics.Rect(); it.getBoundsInScreen(b)
            ControlRevealTarget.blocks(it.isVisibleToUser && it.isClickable, b.contains(x, y), ui.label(it),
                b.width().toDouble() * b.height() / (r.width().toDouble() * r.height()))
        }) return false
        revealCount++; lastReveal = now
        Logger.i("ADREVEAL", "single tap on observed canvas count=$revealCount bounds=$r point=$x,$y")
        ui.tap(x, y)
        kotlinx.coroutines.delay(120)
        revealedPng = ui.captureVisual(includePng = true).png
        revealedPngAt = android.os.SystemClock.elapsedRealtime()
        revealedPng?.let { Evidence.writeBytes(svc, "ad_${AutoAccessibilityService.currentRunId}_${lastReveal}_controls_reveal_${revealCount}.png", it) }
        return true
    }
    /** A painted control uses its observed glyph bounds, never the entire canvas centre. */
    fun controlBounds(node: android.view.accessibility.AccessibilityNodeInfo): android.graphics.Rect? {
        canvasControlBounds?.let {
            if (android.os.SystemClock.elapsedRealtime() - canvasControlAt > 1500L || !isOpaquePlayable()) return null
            return android.graphics.Rect(it)
        }
        if (!node.refresh() || !node.isVisibleToUser || !node.isEnabled) return null
        val r = android.graphics.Rect(); node.getBoundsInScreen(r)
        return r.takeIf { it.width() > 0 && it.height() > 0 }
    }
    private suspend fun paintedCanvasControl(): android.view.accessibility.AccessibilityNodeInfo? {
        if (revealCount == 0 || !isOpaquePlayable()) return null
        val canvas = ui.foregroundNodes().firstOrNull { it.isVisibleToUser && it.isEnabled && it.viewIdResourceName == "application-canvas" } ?: return null
        val r = android.graphics.Rect(); canvas.getBoundsInScreen(r)
        var png = if (android.os.SystemClock.elapsedRealtime() - revealedPngAt < 1200L) revealedPng else null
        if (png == null) {
            png = ui.captureVisual(includePng = true).png ?: return null
            revealedPng = png; revealedPngAt = android.os.SystemClock.elapsedRealtime()
        }
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        val result = try {
            val height = (r.height() * .22).toInt()
            if (r.left < 0 || r.top < 0 || r.width() <= 0 || height <= 0 || r.right > bitmap.width || r.top + height > bitmap.height) null else {
                val pixels = IntArray(r.width() * height)
                bitmap.getPixels(pixels, 0, r.width(), r.left, r.top, r.width(), height)
                CanvasAdControl.find(r.width(), height, pixels)
            }
        } finally { bitmap.recycle() } ?: return null
        // Triangle/bar is also used by the observed Google Play promotion pill.
        // Geometry alone cannot authorize a skip tap.
        if (!CanvasControlPolicy.canTap(result.kind)) return null
        val b = result.bounds
        canvasControlBounds = android.graphics.Rect(r.left+b.left, r.top+b.top, r.left+b.right, r.top+b.bottom)
        canvasControlAt = revealedPngAt
        observedTapOnly = true
        val proof = result.kind + ":" + canvasControlBounds
        if (proof != canvasControlProof) {
            canvasControlProof = proof
            Logger.i("ADCLOSE", "observedPaintedCanvasControl=" + result.kind + " bounds=" + canvasControlBounds)
            Evidence.writeBytes(svc, "ad_" + AutoAccessibilityService.currentRunId + "_" + canvasControlAt + "_painted_" + result.kind + ".png", png)
        }
        return canvas
    }
    suspend fun restoreAdAfterStore() {
        ui.refreshCache()
        val now=android.os.SystemClock.elapsedRealtime()
        val token=flowOwner
        fun live()=token!=null&&token.app=="ANA"&&token.run==flowRun&&
            CheckinRuntime.ownership.valid(token,android.os.SystemClock.elapsedRealtime())&&
            AutoAccessibilityService.currentRunId==flowRun&&AutoAccessibilityService.isRunning&&
            !AutoAccessibilityService.stopRequested&&TokyoDate.of()==flowDate
        val activity=AutoAccessibilityService.foregroundActivity
        val nativePangle=ui.currentPackage()==pkg&&AdSdkWindows.contains(activity)&&
            activity.startsWith("com.bytedance.sdk.openadsdk.activity.")
        val chromePage=ui.currentPackage()=="com.android.chrome"&&
            activity=="org.chromium.chrome.browser.ChromeTabbedActivity"
        if(chromeReturn.shouldBack(now,nativePangle,chromePage,live())){
            Evidence.write(svc,"ad_"+flowRun+"_chrome_return_"+System.currentTimeMillis()+".txt",
                "Same observed Pangle ad -> ChromeTabbedActivity; one bounded Back. No new ad or reward inferred.")
            var returned=false
            if(token!=null)CheckinRuntime.ownership.action(token,android.os.SystemClock.elapsedRealtime(),false){
                if(live()&&ui.currentPackage()=="com.android.chrome"&&
                    AutoAccessibilityService.foregroundActivity=="org.chromium.chrome.browser.ChromeTabbedActivity")returned=ui.back()
            }
            Logger.i("ADBROWSER","bound Pangle Chrome return Back attempts=1 dispatched="+returned)
            kotlinx.coroutines.delay(900);ui.refreshCache();return
        }
        val l = ui.foregroundNodes().filter { it.isVisibleToUser }.map { ui.label(it) }
        if(PlayStoreReturnPolicy.mayBackFromIntro(ui.currentPackage(),l,seenStoreSheet,storeBacks)){
            Evidence.write(svc,"ad_"+AutoAccessibilityService.currentRunId+"_play_notification_intro_"+System.currentTimeMillis()+".txt","Observed Play notification introduction after this ad store sheet; normal Back only; no permission or install action.")
            storeBacks++
            Logger.i("ADSTORE","dismiss observed Play notification introduction with Back; bound sheet=true back="+storeBacks)
            ui.back();kotlinx.coroutines.delay(900);ui.refreshCache();return
        }
        val home = seenStoreSheet && !AdSignals.playSheet(ui.currentPackage(), l) && AdSignals.playHome(ui.currentPackage(), l)
        if (pangleReplay.shouldReturn(home)) {
            Evidence.write(svc, "ad_" + AutoAccessibilityService.currentRunId + "_pangle_store_replay.txt", pangleReplay.description())
            ui.captureVisual(includePng = true).png?.let { Evidence.writeBytes(svc,
                "ad_" + AutoAccessibilityService.currentRunId + "_pangle_store_replay.png", it) }
            if (ui.launchApp(pkg)) {
                pangleReplay.markReturned()
                Logger.i("ADSTORE", "same Pangle document repeated after store return; launch ANA once, no receipt inferred")
                kotlinx.coroutines.delay(1800)
                return
            }
        }
        if (seenStoreSheet && storeBacks < 2 && !AdSignals.playSheet(ui.currentPackage(), l) && AdSignals.playHome(ui.currentPackage(), l)) {
            storeBacks++
            Logger.i("ADSTORE", "return from observed Play home after sheet dismissal back=$storeBacks")
            ui.back()
            kotlinx.coroutines.delay(900)
        }
    }
    private fun pangleSurface(): Boolean = ui.currentPackage() == pkg && (
        AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity) && AutoAccessibilityService.foregroundActivity.startsWith("com.bytedance.sdk.openadsdk.activity.") ||
        ui.foregroundNodes().any { it.isVisibleToUser && it.viewIdResourceName == "__root_view__" })
    private fun fyberSurface(): Boolean = ui.currentPackage() == pkg && ui.foregroundNodes().any {
        it.isVisibleToUser && (it.contentDescription?.toString() == "IAInterstitialView" || it.viewIdResourceName == "$pkg:id/ia_endcard_video_overlay")
    }
    private fun unitySurface(): Boolean = ui.currentPackage() == pkg && (
        AutoAccessibilityService.foregroundActivity == "com.unity3d.ads.adplayer.FullScreenWebViewDisplay" ||
        AdSignals.unityEndCard(ui.foregroundNodes().filter { it.isVisibleToUser && it.className?.toString() == "android.widget.Button" }.map { ui.label(it) }) ||
        UnityClosePolicy.privacyOverlay(ui.foregroundNodes().filter{it.isVisibleToUser}.map{ui.label(it)}))
    private fun mbridgeSurface(): Boolean = ui.currentPackage() == pkg && (
        AutoAccessibilityService.foregroundActivity.startsWith("com.mbridge.msdk.") ||
        ui.foregroundNodes().any { it.isVisibleToUser && it.viewIdResourceName?.startsWith("$pkg:id/mbridge_") == true })
    private fun ogurySurface():Boolean {val n=ui.foregroundNodes().filter{it.isVisibleToUser};return OguryClosePolicy.surface(ui.currentPackage(),AutoAccessibilityService.foregroundActivity,n.mapNotNull{it.viewIdResourceName}.toSet(),n.map{ui.label(it)})}
    fun isNativeChallengeReceipt():Boolean {val n=ui.foregroundNodes().filter{it.isVisibleToUser};return NativeChallengeReceiptPolicy.panel(ui.currentPackage(),n.mapNotNull{it.viewIdResourceName}.toSet(),n.map{ui.label(it)})}
    fun isAdSurface(): Boolean = ui.currentPackage() == pkg && !isNativeChallengeReceipt() && (
        AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity) || pangleSurface() || fyberSurface() || unitySurface() || mbridgeSurface() || ogurySurface() || isOpaquePlayable())
    fun isRecoverableSurface(): Boolean = isAdSurface() || AdSignals.playSheet(ui.currentPackage(),
        ui.foregroundNodes().filter { it.isVisibleToUser }.map { ui.label(it) }) ||
        seenStoreSheet && (AdSignals.playHome(ui.currentPackage(), ui.foregroundNodes().map { ui.label(it) }) ||
            PlayStoreReturnPolicy.notificationIntro(ui.currentPackage(),ui.foregroundNodes().filter{it.isVisibleToUser}.map{ui.label(it)}))
    fun isNativePangleRender(): Boolean = pangleSurface() && ui.foregroundNodes().any {
        it.isVisibleToUser && it.className?.toString() == "android.view.ViewGroup" && ui.label(it) == "virtualNode"
    }
    var observedAdvanceControl=false
        private set
    suspend fun identifyClose(): android.view.accessibility.AccessibilityNodeInfo? {
        observedAdvanceControl=false
        canvasControlBounds = null
        if(isNativeChallengeReceipt())return null
        val activeNodes = ui.foregroundNodes()
        val activeLabels = activeNodes.filter { it.isVisibleToUser }.map { ui.label(it) }
        if(mbridgeSurface()&&MBridgePlaybackPolicy.mustWait(activeLabels)){
            if(!AutoAccessibilityService.stopRequested&&MBridgePlaybackPolicy.mayContinue(activeLabels,playbackContinueRequests)){
                val resume=activeNodes.filter{it.isVisibleToUser&&it.isEnabled&&it.isClickable&&
                    it.className?.toString()=="android.widget.TextView"&&ui.label(it)=="Continue"}.singleOrNull()
                if(resume!=null&&AccountSafety.authorize(svc,"mbridge_same_ad_continue")){
                    playbackContinueRequests++
                    Evidence.write(svc,"ad_"+AutoAccessibilityService.currentRunId+"_mbridge_continue_"+System.currentTimeMillis()+".txt",
                        "Same ad warning: Confirm to close? / You will not be rewarded after closing the window. Continue only; no new ad or reward inferred. requests="+playbackContinueRequests)
                    Logger.i("ADPLAYBACK","sdk=mbridge resume_same_ad_Continue request="+playbackContinueRequests)
                    ui.click(resume);kotlinx.coroutines.delay(800);ui.refreshCache()
                }
            }
            return null // A visible X is not permission to forfeit a still-counting reward.
        }
        observedStoreOverlay = AdSignals.playSheet(ui.currentPackage(), activeLabels)
        pangleReplay.observeSheet(observedStoreOverlay)
        if (observedStoreOverlay) seenStoreSheet = true
        observedTapOnly = observedStoreOverlay
        if (ui.currentPackage() == "com.android.vending") {
            if (!observedStoreOverlay) return null
            return activeNodes.filter { it.isVisibleToUser && it.isEnabled && ui.label(it).trim() in setOf("关闭", "Close", "閉じる") }.singleOrNull()
                ?: activeNodes.filter { it.isVisibleToUser && it.isEnabled && ui.label(it).trim() in setOf("关闭工作表", "Close sheet", "シートを閉じる") }.singleOrNull()
        }
        if (ui.currentPackage() != pkg) return null
        activeNodes.singleOrNull{GoogleNativeClosePolicy.matches(AutoAccessibilityService.foregroundActivity,
            it.className?.toString().orEmpty(),ui.label(it),it.isVisibleToUser,it.isEnabled,it.isClickable)}?.let{
            observedTapOnly=true;Logger.i("ADCLOSE","sdk=google observedNativeInterstitialCloseButton=true");return it
        }
        if (pangleSurface()) pangleReplay.observeDocument(activeNodes.filter { it.isVisibleToUser }.mapNotNull { it.viewIdResourceName })
        val google = AutoAccessibilityService.foregroundActivity == "com.google.android.gms.ads.AdActivity" &&
            activeNodes.any { it.viewIdResourceName == "app-interstitial-slot" }
        if (unitySurface() || mbridgeSurface() || google || pangleSurface()) observedTapOnly = true
        if(ogurySurface()){
            val visible=activeNodes.filter{it.isVisibleToUser};val ids=visible.mapNotNull{it.viewIdResourceName}.toSet();val labels=visible.map{ui.label(it)};val density=svc.resources.displayMetrics.density
            visible.filter{n->val r=android.graphics.Rect();n.getBoundsInScreen(r);n.isEnabled&&n.isClickable&&OguryClosePolicy.control(ui.currentPackage(),AutoAccessibilityService.foregroundActivity,ids,labels,n.viewIdResourceName,ui.label(n),n.className.toString(),r.width()/density,r.height()/density)}.singleOrNull()?.let{
                observedTapOnly=true;Logger.i("ADCLOSE","sdk=ogury observedVendorClose=true resource="+it.viewIdResourceName);return it
            }
        }
        val unity=unitySurface()
        if(unity&&UnityClosePolicy.privacyOverlay(activeNodes.filter{it.isVisibleToUser}.map{ui.label(it)})){
            val back=activeNodes.filter{it.isVisibleToUser&&it.isEnabled&&it.isClickable&&ui.label(it)=="button:privacy-back"}.singleOrNull()
            if(back!=null){observedTapOnly=true;Logger.i("ADCLOSE","sdk=unity privacy_back_navigation_only");return back}
            return null
        }
        fun unitySafe(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(!unity)return true
            val b=android.graphics.Rect();n.getBoundsInScreen(b);val m=svc.resources.displayMetrics
            return UnityClosePolicy.endControl(b.left,b.top,b.width(),b.height(),m.widthPixels,m.heightPixels,m.density) ||
                UnityClosePolicy.labelledEndControl(ui.label(n),n.className.toString(),n.isClickable,activeLabels,
                    b.left,b.top,b.width(),b.height(),m.widthPixels,m.heightPixels,m.density)
        }
        activeNodes.firstOrNull{it.isVisibleToUser&&it.isEnabled&&AdSignals.closeLabel(ui.label(it))&&unitySafe(it)}?.let{return it}
        activeNodes.firstOrNull { it.isVisibleToUser && it.isEnabled &&
            AdSignals.pangleNativeClose(AutoAccessibilityService.foregroundActivity, ui.label(it)) }?.let {
            observedTapOnly = true
            Logger.i("ADCLOSE", "sdk=pangle observedNativeCloseLabel=" + ui.label(it))
            return it
        }
        // Observed Google next-stage control; this action never confirms completion.
        if (google) {
            val ids=activeNodes.filter{it.isVisibleToUser}.mapNotNull{it.viewIdResourceName}.toSet()
            activeNodes.filter{n->n.isVisibleToUser&&n.isEnabled&&GoogleDismissPolicy.noThanks(ui.currentPackage(),
                AutoAccessibilityService.foregroundActivity,ids,n.viewIdResourceName,n.className.toString(),n.isClickable,
                (0 until n.childCount).mapNotNull{i->n.getChild(i)?.takeIf{it.isVisibleToUser}?.let{ui.label(it)}})}
                .singleOrNull()?.let{observedTapOnly=true;Logger.i("ADCLOSE","sdk=google observedDismissButton=NO_THANKS");return it}
            activeNodes.filter { it.isVisibleToUser && it.isEnabled && it.isClickable &&
                GoogleDismissPolicy.advanceControl(ui.currentPackage(),AutoAccessibilityService.foregroundActivity,
                    ids,it.className.toString(),it.isClickable,ui.label(it)) }
                .singleOrNull()?.let { observedTapOnly = true; observedAdvanceControl=true; Logger.i("ADCLOSE", "sdk=google observedSkipButton=true"); return it }
        }
        if (mbridgeSurface()) {
            val m=svc.resources.displayMetrics
            activeNodes.filter{n->val r=android.graphics.Rect();n.getBoundsInScreen(r)
                n.isVisibleToUser&&n.isEnabled&&MBridgePlaybackPolicy.playableControl(n.viewIdResourceName,n.className.toString(),
                    n.isClickable,r.left,r.top,r.width(),r.height(),m.widthPixels,m.heightPixels,m.density,activeLabels)}
                .singleOrNull()?.let{observedTapOnly=true;Logger.i("ADCLOSE","sdk=mbridge observedPlayableControl="+it.viewIdResourceName);return it}
            val endCloseIds = setOf("mbridge_full_iv_close", "mbridge_full_rl_close", "mbridge_interstitial_iv_close",
                "mbridge_iv_close", "mbridge_iv_vastclose", "mbridge_order_view_iv_close", "mbridge_vec_iv_close", "mbridge_windwv_close")
            val matches = activeNodes.filter { n ->
                val r = android.graphics.Rect(); n.getBoundsInScreen(r)
                n.isVisibleToUser && n.isEnabled && n.viewIdResourceName?.substringAfterLast('/') in endCloseIds &&
                    r.width() > 0 && r.height() > 0 && ui.label(n).isBlank()
            }
            matches.minByOrNull { val r = android.graphics.Rect(); it.getBoundsInScreen(r); r.width() * r.height() }?.let {
                observedTapOnly = true
                Logger.i("ADCLOSE", "sdk=mbridge observedCloseResource=" + it.viewIdResourceName)
                return it
            }
        }
        paintedCanvasControl()?.let { return it }
        val fyber = fyberSurface()
        if (fyber) {
            val matches = activeNodes.filter { n ->
                val l = ui.label(n)
                n.isVisibleToUser && n.isEnabled && n.isClickable && n.className?.toString() == "android.widget.Button" &&
                    l.startsWith("svg+xml;base64,") && runCatching {
                        AdCloseIcon.isCloseSvg(String(android.util.Base64.decode(l.substringAfter(','), android.util.Base64.DEFAULT), Charsets.UTF_8))
                    }.getOrDefault(false)
            }
            matches.singleOrNull()?.let {
                observedTapOnly = true
                val r = android.graphics.Rect(); it.getBoundsInScreen(r)
                Logger.i("ADCLOSE", "sdk=fyber observedSvgCross=true bounds=$r")
                return it
            }
        }
        val pangle = pangleSurface()
        val knownFullscreen = AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity) || isOpaquePlayable()
        if (!pangle && !fyber && !knownFullscreen) return null
        val density = svc.resources.displayMetrics.density
        val candidates = ui.foregroundNodes().filter {
            val r = android.graphics.Rect(); it.getBoundsInScreen(r)
            it.isVisibleToUser && it.isEnabled && it.childCount == 0 && ui.label(it).isBlank() &&
                (if (fyber) it.isClickable && it.viewIdResourceName == "$pkg:id/ia_clickable_close_button" &&
                    r.width() / density in 12f..64f && r.height() / density in 12f..64f
                else if (pangle) it.className?.toString() in setOf("android.widget.Image", "android.widget.ImageView") &&
                    r.top < svc.resources.displayMetrics.heightPixels * .25 && r.left > svc.resources.displayMetrics.widthPixels * .5 &&
                    r.width() / density in 12f..64f && r.height() / density in 12f..64f
                else it.isClickable && it.className?.toString() in setOf("android.widget.ImageView", "android.widget.ImageButton", "android.view.View", "android.widget.FrameLayout") &&
                    r.width() / density in 12f..64f && r.height() / density in 12f..64f)
        }
        if (candidates.isEmpty()) return null
        val png = (if (android.os.SystemClock.elapsedRealtime() - revealedPngAt < 1500L) revealedPng else null)
            ?: ui.captureVisual(includePng = true).png ?: return null
        val bmp = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        try {
            val matches = candidates.filter { n ->
                if(!unitySafe(n))return@filter false
                val r = android.graphics.Rect(); n.getBoundsInScreen(r)
                if (r.left < 0 || r.top < 0 || r.right > bmp.width || r.bottom > bmp.height) false else {
                    val pixels = IntArray(r.width() * r.height())
                    bmp.getPixels(pixels, 0, r.width(), r.left, r.top, r.width(), r.height())
                    AdCloseIcon.isWhiteCross(r.width(), r.height(), pixels) || AdCloseIcon.isWhiteCircleCross(r.width(), r.height(), pixels) ||
                        pangle && AdCloseIcon.isWhiteForward(r.width(), r.height(), pixels)
                }
            }
            val matched = matches.singleOrNull()
            observedTapOnly = pangle || fyber
            if (matched != null) {
                val r = android.graphics.Rect(); matched.getBoundsInScreen(r)
                Logger.i("ADCLOSE", "run=" + AutoAccessibilityService.currentRunId + " sdk=" + (if (fyber) "fyber_native" else if (pangle) "pangle" else "five") + " observedVisualEndControl=true bounds=" + r)
                Evidence.writeBytes(svc, "ad_" + AutoAccessibilityService.currentRunId + "_five_close_observed.png", png)
            }
            return matched
        } finally { bmp.recycle() }
    }

}
