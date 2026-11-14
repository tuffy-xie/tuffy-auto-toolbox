package com.anapocket.auto

import kotlinx.coroutines.*
import android.os.SystemClock
import org.json.JSONObject

/** Only current, observed free task entries. An unknown page is evidence, never a successful reward. */
class CheckinController(private val ui:CheckinUi,private val store:CheckinStore,private val run:String,private val date:String) {
    private suspend fun frame():CheckinFrame {
        val f=ui.frame();val text=f.text
        if(Regex("アカウント.{0,12}(停止|制限)|ご利用を制限|本人確認が必要|画像に表示.{0,12}入力|私はロボットでは|追加認証が必要").containsMatchIn(text)){
            val e=ui.evidence(store,run,"human_required")
            currentCoroutineContext().ensureActive();check(ui.owns()){"UI_lease_expired_or_preempted"}
            mutate {
                store.hold("verification_or_account_warning")
                store.tasks().forEach{t->if(store.read(t,date)?.optString("state") !in setOf("SUCCESS","ALREADY_DONE"))store.update(t,run,CheckinState.NEEDS_USER_ACTION,"verification_or_account_warning",evidence=e,date=date)}
            }
            error("verification_or_account_warning")
        }
        return f
    }
    private fun done(task:String)=store.read(task,date)?.optString("state") in setOf("SUCCESS","ALREADY_DONE")
    private fun reserved(task:String)=store.read(task,date)?.optBoolean("reserved")==true
    private suspend fun set(task:String,state:CheckinState,why:String="",evidence:String="",asset:String="",amount:String?=null,confirmed:Boolean=false):JSONObject {
        return mutate {store.update(task,run,state,why,asset,amount,confirmed,evidence,date=date)}
    }
    private suspend fun <T:Any> mutate(block:()->T):T {
        currentCoroutineContext().ensureActive();check(ui.owns()){"UI_lease_expired_or_preempted"}
        return store.withActiveRun(run,block)?:throw CancellationException("checkin_run_replaced")
    }
    private suspend fun capture(label:String)=ui.evidence(store,run,label)
    private suspend fun captureFailure(label:String):String = try {capture(label)}
        catch(e:CancellationException){throw e}
        catch(_:Exception){currentCoroutineContext().ensureActive();check(ui.owns()){"UI_lease_expired_or_preempted"};""}
    private suspend fun target(f:CheckinFrame,pattern:Regex,filter:(CheckinLine)->Boolean={true}):Boolean {
        val line=f.lines.firstOrNull{pattern.containsMatchIn(it.text)&&filter(it)}?:return false
        return ui.click(f,line)
    }
    private suspend fun submitNative(task:String,label:String,legacy:Boolean=false):Boolean {
        repeat(3){
            if(store.read(task,date)?.optString("phase")=="START_SUBMITTED")return false
            val fresh=frame();val control=ui.nativeLabel(label)
            if(control!=null&&ui.click(fresh,control){mutate {store.submitted(task,run,date,legacy,preflightVerified=true)}})return true
            if(store.read(task,date)?.optString("phase")=="START_SUBMITTED")return false
            delay(500) // Retry only preflight; a submitted opportunity is never tapped again.
        }
        return false
    }
    private fun already(text:String)=CheckinReceiptPolicy.alreadyDone(text,text.contains("ご利用ガイド")||Regex("STEP\\s*[0-9]").containsMatchIn(text))
    private fun paid(text:String)=Regex("有料|購入が必要|支払い|決済|円以上.{0,10}(購入|注文)|クレジットカード.{0,10}登録").containsMatchIn(text)
    suspend fun execute(){
        if(store.hold().isNotBlank())return
        try{
            awaitFrame("app_content",35_000){f->when(ui.app){
                CheckinApp.RAKUTEN->Regex("楽天|PointClub|ログイン|ザクザク|もらう|ピックアップ").containsMatchIn(f.text)
                CheckinApp.TIKTOK->TikTokCheckinPolicy.loaded(f.lines)
                else->Regex("いきなり|ダーツ|肉マネー|ホーム|ログイン|クーポン").containsMatchIn(f.text)}}
        }catch(e:IllegalStateException){
            val row=store.read("darts",date)
            val unsubmitted=row?.optString("phase")=="ENTRY_RESERVED"||store.mayLocateUnissuedDartsStart(date)
            if(e.message!="app_content_load_timeout"||ui.app!=CheckinApp.STEAK||!unsubmitted||
                ui.nativeLabel("スタート")==null||!CheckinReceiptPolicy.boundDartsStartPage(ui.nativeText(),true))throw e
            capture("unissued_game_blank_resume_before_normal_back")
            ui.back() // Observed blank retained game; no Start had been dispatched. Never resets a submitted game.
            awaitFrame("unissued_game_home_recovered",20_000){it.text.contains("ホーム")&&it.text.contains("肉")}
        }
        if(ui.app==CheckinApp.RAKUTEN){
            val liveBonus=reserved("bonus")&&!done("bonus")&&ui.nativeLabel("今日のボーナスくじ")!=null
            if(liveBonus){
                task("bonus"){rakutenBonus(true)}
                if(!done("bonus")&&store.read("bonus",date)?.optString("failureReason")!="bonus_official_loading_error_after_submission")return
            }
            val submitted=listOf("lottery1","lottery2").firstOrNull{task->val row=store.read(task,date)
                CheckinLotteryReview.mayReview(row?.optBoolean("reserved")==true,row?.optString("phase").orEmpty(),
                    row?.optString("state").orEmpty(),row?.optString("businessDate").orEmpty(),date)}
            if(submitted!=null&&ui.nativeLabel(CheckinLotteryReview.TITLE)!=null){
                task(submitted){reviewSubmittedLottery(submitted)}
                if(!done(submitted))return // Unknown submitted scenes are never reopened or submitted again.
            }
            val pickupLive=ui.nativeLabel("今日のピックアップ")!=null
            if(pickupLive)task("pickup"){rakutenPickup(true)}
            val pending=if(done("lottery1"))"lottery2" else "lottery1"
            val old=store.read(pending,date)
            val live=reserved(pending)&&!done(pending)&&old?.optString("phase")!="START_SUBMITTED"&&ui.nativeText().contains("楽天PointClubアプリラッキーくじ")
            if(live)task(pending){rakutenLottery(true)}
            task("points"){rakutenPoints()}
            if(!live&&store.hold().isBlank())task("lottery1"){rakutenLottery()}
            if(done("lottery1")&&!done("lottery2")&&store.hold().isBlank())task("lottery2"){rakutenLottery()}
            if(!pickupLive&&store.hold().isBlank())task("pickup"){rakutenPickup()}
            if(store.hold().isBlank())task("bonus"){rakutenBonus()}
        }else if(ui.app==CheckinApp.TIKTOK)tiktok()
        else task("darts"){steak()}
        store.tasks().forEach{if(store.read(it,date)?.optString("state")=="RUNNING")set(it,CheckinState.UNCONFIRMED,"controller_finished_without_receipt")}
    }
    private suspend fun awaitFrame(label:String,timeout:Long=45_000,predicate:suspend(CheckinFrame)->Boolean):CheckinFrame {
        val until=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<until){val f=frame();if(predicate(f))return f;delay(650)}
        capture(label+"_load_timeout");error(label+"_load_timeout")
    }
    private suspend fun task(task:String,block:suspend()->Unit){
        if(store.hold().isNotBlank())return
        if(done(task)&&!(task=="lottery1"&&!done("lottery2")))return
        try{withTimeout(180_000){block()}}
        catch(e:CheckinPerceptionUnavailable){throw e}
        catch(e:TimeoutCancellationException){currentCoroutineContext().ensureActive();check(ui.owns()){"UI_lease_expired_or_preempted"};set(task,if(reserved(task))CheckinState.UNCONFIRMED else CheckinState.BLOCKED,"task_timeout",captureFailure(task+"_timeout"))}
        catch(e:CancellationException){throw e}
        catch(e:Exception){
            currentCoroutineContext().ensureActive();check(ui.owns()){"UI_lease_expired_or_preempted"}
            val why=e.message?.take(160).orEmpty()
            if(why in setOf("login_required","member_login_required")){
                val proof=captureFailure("login_required")
                mutate {
                    store.hold("login_required")
                    store.tasks().forEach{t->if(!done(t))store.update(t,run,CheckinState.NEEDS_USER_ACTION,"login_required",evidence=proof,date=date)}
                }
                return
            }
            val r=store.read(task,date)
            if(r?.optString("state") !in setOf("SUCCESS","ALREADY_DONE","NEEDS_USER_ACTION"))set(task,if(reserved(task))CheckinState.UNCONFIRMED else CheckinState.BLOCKED,why,captureFailure(task+"_blocked"))
        }
    }
    private suspend fun reviewSubmittedLottery(task:String){
        val until=SystemClock.elapsedRealtime()+45_000
        while(SystemClock.elapsedRealtime()<until){
            val f=frame()
            if(ui.nativeLabel(CheckinLotteryReview.TITLE)==null)break
            val result=CheckinLotteryReview.explicitResult(f.lines,ui.nativeLabel("start")!=null)
            if(result!=null){
                val proof=capture("lottery_late_result_"+task)
                val lost=result=="LOST"
                val amount=if(lost)"0" else CheckinReceiptPolicy.rewardAmount(f.lines,"ポイント")
                set(task,CheckinState.SUCCESS,if(lost)"draw_completed_no_prize" else "draw_completed_credit_due_later",proof,"楽天ポイント",amount,false)
                return
            }
            delay(900)
        }
        set(task,CheckinState.UNCONFIRMED,"submitted_lottery_result_not_proved_no_replay",capture("lottery_submitted_readonly_review"))
    }
    private suspend fun earn():CheckinFrame {
        val until=SystemClock.elapsedRealtime()+45_000
        while(SystemClock.elapsedRealtime()<until){
            if(ui.node("iv_view_later")){delay(500);continue}
            // A selected bottom-navigation item need not be clickable. Read the current
            // official cards first instead of sending Back from the already-open earn tab.
            if(ui.hasNativeNode("nav_home")){
                val current=frame()
                if(CheckinReceiptPolicy.pointsEntry(current.lines)!=null&&CheckinReceiptPolicy.lotteryEntry(current.lines)!=null)return current
                if(ui.node("nav_earn"))return awaitFrame("earn"){CheckinReceiptPolicy.pointsEntry(it.lines)!=null&&CheckinReceiptPolicy.lotteryEntry(it.lines)!=null}
                delay(500);continue
            }
            if(ui.node("close_button")||ui.node("reward_sps_header_close")||ui.node("reward_sps_portal_header_close")||ui.node("btn_web_close")){
                store.log(run,"EARN_RETURN observed_close_control=true");delay(900);continue
            }
            // A missing node can be a timed-out query, not proof that Back is safe.
            // Preserve unknown pages; never back out merely because an ID was unavailable.
            delay(600)
        }
        val f=frame();if(f.text.contains("ログイン")||f.text.contains("サインイン"))error("login_required")
        error("earn_entry_unavailable")
    }
    private suspend fun rakutenPoints(){
        if(done("points"))return
        set("points",CheckinState.RUNNING)
        earn()
        val home=awaitFrame("daily_points_status"){CheckinReceiptPolicy.pointsStatusReady(it.lines)}
        if(CheckinReceiptPolicy.pointsDone(home.lines)){set("points",CheckinState.ALREADY_DONE,evidence=capture("points_official_already_done"));return}
        if(reserved("points")){set("points",CheckinState.UNCONFIRMED,"prior_points_submission_home_review_no_replay",capture("points_home_review"));return}
        val entry=CheckinReceiptPolicy.pointsEntry(home.lines)?:error("real_daily_points_card_not_found")
        check(ui.click(home,entry)){"daily_points_card_changed"}
        var f=awaitFrame("points_offer"){it.text.contains("楽天SPS")&&Regex("[+＋]\\s*1\\s*ポイント").containsMatchIn(ui.nativeText())};val initial=capture("points_offer")
        if(already(f.text)){set("points",CheckinState.ALREADY_DONE,evidence=initial);return}
        if(reserved("points")){set("points",CheckinState.UNCONFIRMED,"previous_submission_needs_official_reconciliation",initial);return}
        check(!f.text.contains("ご利用ガイド")&&!f.text.contains("STEP")){"guide_is_not_reward"}
        check(Regex("[+＋]\\s*1\\s*ポイント").containsMatchIn(ui.nativeText())){"current_sps_free_offer_missing"}
        check(mutate {store.reserve("points",run,date)}){"already_reserved"}
        check(ui.node("sps_osusume_card_view"){mutate {store.submitted("points",run,date,preflightVerified=true)}}){"sps_offer_node_changed"}
        val until=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<until){
            delay(1200);f=frame()
            if(ui.hasNativeNode("engage_done_view")&&ui.nativeLabel("済")!=null){
                val engaged=capture("points_native_engagement_done")
                check(ui.node("close_button")){"completed_sps_close_control_missing"}
                earn()
                val daily=awaitFrame("engagement_daily_status"){CheckinReceiptPolicy.pointsStatusReady(it.lines)}
                val proof=capture("engagement_daily_status")
                set("points",if(CheckinReceiptPolicy.pointsDone(daily.lines))CheckinState.SUCCESS else CheckinState.UNCONFIRMED,
                    "official_engagement_done_daily_card_review_credit_not_confirmed",engaged+";"+proof)
                return
            }
            val guide=f.text.contains("ご利用ガイド")||f.text.contains("STEP")
            if(CheckinReceiptPolicy.realReceipt(true,guide,true,f.text)&&Regex("(?<![0-9])1\\s*ポイント(?:を)?(?:ゲット|獲得)").containsMatchIn(f.text)){
                val e=capture("points_receipt")
                val planned=f.text.contains("獲得予定")
                val backHome=earn()
                val dailyDone=CheckinReceiptPolicy.pointsDone(backHome.lines)
                val official=capture("points_daily_status")
                set("points",if(dailyDone)CheckinState.SUCCESS else CheckinState.UNCONFIRMED,if(!dailyDone)"point_receipt_seen_daily_mission_not_confirmed" else if(planned)"official_earned_pending_credit" else "official_point_receipt",e+";"+official,"楽天ポイント","1",false)
                return
            }
            if(already(f.text)){set("points",CheckinState.ALREADY_DONE,evidence=capture("points_done"));return}
        }
        set("points",CheckinState.UNCONFIRMED,"no_fresh_official_point_receipt",capture("points_unconfirmed"))
    }
    private suspend fun rakutenLottery(resumeLive:Boolean=false){
        val task=RakutenLotteryPolicy.next(done("lottery1"),reserved("lottery1"),store.read("lottery1",date)?.optString("phase").orEmpty(),done("lottery2"))?:return
        val old=store.read(task,date)
        val legacy=old?.optString("failureReason")=="official_remaining_chances_not_readable"&&old.optString("phase").isBlank()
        if(!resumeLive){
            val home=earn()
            if(CheckinReceiptPolicy.cardDone(home.lines,CheckinReceiptPolicy.lotteryEntry(home.lines)))set("garagara_daily",CheckinState.ALREADY_DONE,"official_daily_card_done_draw_count_not_inferred",capture("garagara_daily_done"))
            val sameUnsubmittedEntry=reserved(task)&&old?.optString("phase")=="ENTRY_RESERVED"
            if(reserved(task)&&!sameUnsubmittedEntry){set(task,CheckinState.UNCONFIRMED,"reserved_lottery_readonly_home_review_no_reopen",capture("lottery_review_home"));return}
            val entry=CheckinReceiptPolicy.lotteryEntry(home.lines)?:error("current_lottery_card_not_found")
            if(!sameUnsubmittedEntry)check(mutate {store.reserve(task,run,date)}){"lottery_already_reserved"}
            else store.log(run,"LOTTERY_REVIEW sameEntry=true startHadNotBeenSubmitted=true")
            check(ui.click(home,entry)){"lottery_card_changed"}
        }else check(reserved(task)&&(old?.optString("phase")=="ENTRY_RESERVED"||legacy)){"live_reserved_entry_not_safe_to_submit"}
        var f=awaitFrame("lottery"){next->already(next.text)||Regex("(?:残り|あと)\\s*0\\s*回").containsMatchIn(next.text)||ui.nativeLabel("start")!=null}
        f=frame() // Native readiness can appear after the OCR snapshot; use a fresh actual image.
        check(f.text.replace(Regex("\\s"),"").contains("楽天PointClubアプリラッキーくじ")){"visible_lottery_header_not_confirmed"}
        val rules=ui.nativeText()
        val evidence=capture("lottery_ready")
        if(already(f.text)||Regex("(?:残り|あと)\\s*0\\s*回").containsMatchIn(f.text)){
            listOf("lottery1","lottery2").forEach{if(!done(it))set(it,CheckinState.ALREADY_DONE,"official_no_chance",evidence)};return
        }
        check(rules.contains("楽天PointClubアプリラッキーくじ")&&rules.contains("1日に2回のみ")){"current_official_lottery_rules_missing"}
        check(!paid(f.text)){"free_lottery_eligibility_not_confirmed"}
        // Current official page exposes an available Start control, but no numeric remaining count.
        // Do not invent a remaining value from the campaign maximum. Process this bound opportunity only.
        store.log(run,"LOTTERY availableStart=true officialRemaining=NOT_DISPLAYED officialMax=2 resumeSameEntry="+resumeLive)
        // 11/02 and 11/12: Start was tapped while the page's loading panda (panda_load) was still shown; the tap was
        // dropped (Start stayed on screen, no result for 45 s) and the draw could not be repeated. Wait for it to go.
        val loadUntil=SystemClock.elapsedRealtime()+12_000
        while(ui.nativeLabel("panda_load")!=null&&SystemClock.elapsedRealtime()<loadUntil)delay(500)
        store.log(run,"LOTTERY loadingGone="+(ui.nativeLabel("panda_load")==null))
        val start=ui.nativeLabel("start")?:error("official_start_control_missing")
        set(task,CheckinState.RUNNING,"same_reserved_opportunity")
        check(submitNative(task,"start",legacy)){"lottery_start_changed_before_dispatch"}
        val before=f.text
        var result:CheckinFrame?=null
        var until=SystemClock.elapsedRealtime()+45_000
        var reviewCloses=0
        while(result==null&&SystemClock.elapsedRealtime()<until){
            delay(900)
            val (ids,labels)=ui.webView()
            if(RakutenReviewModalPolicy.mayClose(ids,labels,reviewCloses)){
                reviewCloses++;capture("lottery_review_modal")
                if(ui.webNode(RakutenReviewModalPolicy.CLOSE_ID)){store.log(run,"LOTTERY review_modal_closed=$reviewCloses");until=maxOf(until,SystemClock.elapsedRealtime()+15_000)}
                continue
            }
            val next=frame()
            if(already(next.text)){set(task,CheckinState.ALREADY_DONE,"official_already_participated",capture("lottery_no_chance"));return}
            val fresh=next.lines.any{Regex("当選|当たり|あたり|はずれ|ハズレ|残念|参加賞").containsMatchIn(it.text)&&!before.contains(it.text)}
            if(fresh&&CheckinReceiptPolicy.realReceipt(true,false,true,next.text))result=next
        }
        val receipt=result?:run{set(task,CheckinState.UNCONFIRMED,"lottery_result_missing",capture("lottery_unknown"));return}
        val proof=capture("lottery_result_"+task)
        val lost=Regex("はずれ|ハズレ|残念").containsMatchIn(receipt.text)
        val amount=CheckinReceiptPolicy.rewardAmount(receipt.lines,"ポイント")
        set(task,CheckinState.SUCCESS,if(lost)"draw_completed_no_prize" else "draw_completed_credit_due_later",proof,"楽天ポイント",if(lost)"0" else amount,false)
        if(task=="lottery1"&&!done("lottery2"))set("lottery2",CheckinState.UNCONFIRMED,"second_chance_needs_official_current_control",proof)
    }
    private suspend fun rakutenPickup(resumeLive:Boolean=false){
        if(done("pickup"))return
        if(!resumeLive){
            val home=earn()
            val anchor=CheckinReceiptPolicy.pickupEntry(home.lines)?:error("pickup_card_not_found")
            if(CheckinReceiptPolicy.cardDone(home.lines,anchor)){set("pickup",CheckinState.ALREADY_DONE,"official_pickup_card_done",capture("pickup_already_done"));return}
            // The browsing page may be reopened to reconcile its official remaining count.
            // The durable per-progress fences below prohibit replaying an uncertain choice.
            if(!reserved("pickup"))check(mutate {store.reserve("pickup",run,date)}){"pickup_already_reserved"}
            check(ui.click(home,anchor)){"pickup_card_changed"}
        }else if(!reserved("pickup"))check(mutate {store.reserve("pickup",run,date)}){"pickup_live_reservation_failed"}
        var f=awaitFrame("pickup"){it.text.contains("今日のピックアップ")}
        // Browse only. Never open the offer, install an app, register or buy its advertised service.
        if(f.text.replace(Regex("\\s"),"").contains("さっそく探す")){
            check(target(f,Regex("さっそく\\s*探す"))){"pickup_intro_changed"};delay(700)
        }
        val until=SystemClock.elapsedRealtime()+110_000
        while(SystemClock.elapsedRealtime()<until){
            f=awaitFrame("pickup_current_progress",20_000){it.text.contains("今日のピックアップ")&&(CheckinReceiptPolicy.pickupRemaining(it.text)!=null||CheckinReceiptPolicy.pickupEnd(it.text))}
            check(f.text.contains("今日のピックアップ")){"pickup_foreground_header_changed"}
            val remaining=CheckinReceiptPolicy.pickupRemaining(ui.nativeText())?:CheckinReceiptPolicy.pickupRemaining(f.text)
            if(remaining==null||remaining==0){
                val proof=capture("pickup_last_page")
                // The end of the browsing stack is not itself a credited reward or official completion.
                if(CheckinReceiptPolicy.pickupEnd(f.text)){
                    if(!ui.node("btn_web_close"))ui.back()
                    earn()
                    awaitFrame("pickup_daily_status"){CheckinReceiptPolicy.cardDone(it.lines,CheckinReceiptPolicy.pickupEntry(it.lines))}
                    set("pickup",CheckinState.SUCCESS,"official_pickup_card_done_no_points_inferred",proof+";"+capture("pickup_daily_done"));return
                }
                set("pickup",CheckinState.UNCONFIRMED,"pickup_progress_or_result_not_recognized",proof);return
            }
            val next=f.lines.firstOrNull{it.text.replace(Regex("\\s"),"")=="次へ"}
            if(next!=null&&f.text.contains("おトクな情報をチェック")&&f.text.contains("PR")){
                check(mutate {store.pickupNext(run,date,remaining)}){"pickup_pr_next_uncertain_no_replay"}
                check(ui.click(f,next)){"pickup_pr_next_changed"}
                awaitFrame("pickup_pr_departure",20_000){!it.text.contains("おトクな情報をチェック")}
                continue
            }
            f=awaitFrame("pickup_neutral_control",12_000){it.lines.any{l->l.text.replace(Regex("\\s"),"").contains("そこまで")}}
            val skip=f.lines.first{it.text.replace(Regex("\\s"),"").contains("そこまで")}
            check(mutate {store.pickupStep(run,date,remaining)}){"pickup_same_progress_must_be_reconciled_not_reclicked"}
            check(ui.click(f,skip)){"pickup_skip_changed_after_reservation"}
            awaitFrame("pickup_progress",15_000){next->val n=CheckinReceiptPolicy.pickupRemaining(ui.nativeText())?:CheckinReceiptPolicy.pickupRemaining(next.text);n==null||n<remaining}
        }
        set("pickup",CheckinState.UNCONFIRMED,"pickup_bounded_timeout",capture("pickup_timeout"))
    }
    private suspend fun rakutenBonus(resumeLive:Boolean=false){
        if(done("bonus"))return
        val initial=store.read("bonus",date)
        if(resumeLive&&initial?.optString("phase")=="ENTRY_RESERVED"&&initial.optString("entryRunId")!=run){
            capture("unsubmitted_bonus_entry_refresh_after_controller_restart")
            check(ui.node("btn_web_close")){"stale_unsubmitted_bonus_close_unavailable"}
            // Refresh only an entry for which Start has never been submitted. An old
            // WebView can retain an expired session; a submitted draw is never restarted.
            rakutenBonus(false);return
        }
        if(!resumeLive){
        var home=earn()
        fun cards(f:CheckinFrame)=Triple(CheckinReceiptPolicy.pointsDone(f.lines),
            CheckinReceiptPolicy.cardDone(f.lines,CheckinReceiptPolicy.lotteryEntry(f.lines)),
            CheckinReceiptPolicy.cardDone(f.lines,CheckinReceiptPolicy.pickupEntry(f.lines)))
        // The card art appears before the 達成 labels under it; a freshly opened app needs a moment.
        val statusUntil=SystemClock.elapsedRealtime()+15_000
        while(cards(home).toList().any{!it}&&SystemClock.elapsedRealtime()<statusUntil){delay(1000);home=frame()}
        val (points,lottery,pickup)=cards(home)
        if(lottery)set("garagara_daily",CheckinState.ALREADY_DONE,"official_daily_card_done_draw_count_not_inferred",capture("bonus_daily_prerequisites"))
        // 11/10-11/12: the 1ポイントミッション card read 達成 after the visit although the local task was BLOCKED
        // (no +1 offer loaded), and the bonus then stopped on bonus_official_prerequisite_records_missing.
        // Rakuten's own 達成 mark is the record; it is copied, nothing is tapped and no point is inferred.
        if(points&&!done("points"))set("points",CheckinState.ALREADY_DONE,"official_daily_card_done_points_not_inferred",capture("bonus_points_card_done"))
        if(pickup&&!done("pickup"))set("pickup",CheckinState.ALREADY_DONE,"official_pickup_card_done",capture("bonus_pickup_card_done"))
        val old=store.read("bonus",date)
        if(CheckinBonusPolicy.drawnToday(home.lines)){
            when(CheckinBonusPolicy.drawnState(reserved("bonus"),old?.optString("phase").orEmpty())){
                CheckinState.SUCCESS->{set("bonus",CheckinState.SUCCESS,"bonus_official_drawn_marker_after_submitted_draw",capture("bonus_official_drawn_marker"),"楽天ポイント",null,false);return}
                CheckinState.ALREADY_DONE->{set("bonus",CheckinState.ALREADY_DONE,"bonus_official_drawn_marker",capture("bonus_official_drawn_marker"));return}
                else->{}
            }
        }
        val decision=CheckinBonusPolicy.entry(points&&lottery&&pickup,reserved("bonus"),old?.optString("phase").orEmpty())
        if(decision==CheckinBonusPolicy.Entry.BLOCKED){set("bonus",CheckinState.BLOCKED,"three_official_daily_cards_not_all_done",capture("bonus_not_yet_eligible"));return}
        if(decision==CheckinBonusPolicy.Entry.KEEP_UNCONFIRMED){set("bonus",CheckinState.UNCONFIRMED,"prior_bonus_submission_review_no_replay",capture("bonus_review"));return}
        val entry=CheckinReceiptPolicy.bonusEntry(home.lines)?:error("bonus_current_entry_missing")
        if(decision==CheckinBonusPolicy.Entry.REVIEW_SUBMITTED)store.log(run,"BONUS_REVIEW reopen_submitted_draw_read_only no_start")
        else if(!reserved("bonus"))check(mutate {store.reserve("bonus",run,date)}){"bonus_already_reserved"}
        check(ui.click(home,entry)){"bonus_entry_changed"}
        }
        awaitFrame("bonus_page"){ui.nativeLabel("今日のボーナスくじ")!=null}
        check(listOf("points","pickup","garagara_daily").all{done(it)}){"bonus_official_prerequisite_records_missing"}
        if(store.read("bonus",date)?.optString("phase")=="ENTRY_RESERVED"){
            val f=frame();val text=ui.nativeText().replace(Regex("\\s"),"")
            check(text.contains("下記3つを達成することで")&&text.contains("抽選を毎日楽しめます")&&!paid(f.text)){"bonus_current_free_daily_rules_not_verified"}
            if(already(f.text)){set("bonus",CheckinState.ALREADY_DONE,evidence=capture("bonus_official_already_done"));return}
            capture("bonus_verified_free_start")
            check(submitNative("bonus","スタート")){"bonus_start_changed_before_dispatch"}
        }
        check(store.read("bonus",date)?.optString("phase")=="START_SUBMITTED"){"bonus_not_bound_to_submitted_opportunity"}
        val until=SystemClock.elapsedRealtime()+45_000
        while(SystemClock.elapsedRealtime()<until){
            val f=frame();check(ui.nativeLabel("今日のボーナスくじ")!=null){"bonus_result_header_changed"}
            if(CheckinBonusPolicy.loadingError(f.text)){
                set("bonus",CheckinState.UNCONFIRMED,"bonus_official_loading_error_after_submission",capture("bonus_official_loading_error_no_replay"));return
            }
            val stop=ui.nativeLabel("ストップ")
            val row=store.read("bonus",date)
            if(CheckinBonusPolicy.stopReady(f.text,row?.optString("phase")=="START_SUBMITTED",stop!=null,row?.optBoolean("stopSubmitted")==true)){
                if(ui.click(f,stop!!){mutate{store.submitBonusStop(run,date)}}){delay(900);continue}
            }
            val result=CheckinBonusPolicy.outcome(f.lines,ui.nativeLabel("スタート")!=null||stop!=null)
            if(result!=null){
                val lost=result=="LOST"
                set("bonus",CheckinState.SUCCESS,if(lost)"bonus_draw_completed_no_prize" else "bonus_draw_completed_credit_due_next_day",
                    capture("bonus_official_result"),"楽天ポイント",if(lost)"0" else CheckinReceiptPolicy.rewardAmount(CheckinBonusPolicy.aboveFooter(f.lines),"ポイント"),false)
                return
            }
            if(already(f.text)){set("bonus",CheckinState.ALREADY_DONE,evidence=capture("bonus_official_already_done"));return}
            delay(900)
        }
        set("bonus",CheckinState.UNCONFIRMED,"bonus_submitted_result_not_yet_proved_no_replay",capture("bonus_submitted_current_scene"))
    }
    private suspend fun steak(){
        if(done("darts"))return
        val previouslyReserved=reserved("darts")
        var prior=store.read("darts",date)
        val locateUnissued=store.mayLocateUnissuedDartsStart(date)
        if(!previouslyReserved){
            var home=frame();val height=ui.svc.resources.displayMetrics.heightPixels
            if(home.lines.none{it.text.trim()=="ホーム"&&it.top>height*.85}&&store.olderTaskExists("darts",date)&&
                Regex("ダーツ|肉マネー|クーポン").containsMatchIn(home.text)){
                capture("prior_date_game_navigation_only_today_not_submitted")
                ui.back() // Leave the old page; every previous-date record remains intact.
                home=awaitFrame("steak_home_after_prior_date"){it.lines.any{l->l.text.trim()=="ホーム"&&l.top>height*.85}||it.text.contains("ログイン")}
            }
            target(home,Regex("^ホーム$")){it.top>height*.85}
        }
        if(previouslyReserved&&prior?.optString("phase")=="START_SUBMITTED"){
            val startReady=ui.nativeLabel("スタート")!=null&&CheckinReceiptPolicy.boundDartsStartPage(ui.nativeText(),true)
            if(mutate {store.reconcileUnissuedDartsStart(run,date,startReady)})prior=store.read("darts",date)
        }
        if(previouslyReserved&&prior?.optString("phase")=="START_SUBMITTED"&&!locateUnissued){
            val current=awaitFrame("darts_existing_scene",35_000){CheckinOcr.dartPull(it.png,it.lines)!=null||CheckinReceiptPolicy.realReceipt(true,false,true,it.text)||already(it.text)||it.text.contains("ホーム")||it.text.contains("ログイン")}
            val arrowReady=current.text.contains("ダーツ")&&CheckinOcr.dartPull(current.png,current.lines)!=null
            mutate {store.reconcileUnissuedDartsPull(run,date,arrowReady)}
            if(arrowReady||CheckinReceiptPolicy.realReceipt(true,false,true,current.text)){set("darts",CheckinState.RUNNING);dartsResult(current);return}
        }
        set("darts",CheckinState.RUNNING)
        val liveEntry=previouslyReserved&&prior?.optString("phase")=="ENTRY_RESERVED"&&CheckinReceiptPolicy.boundDartsStartPage(ui.nativeText(),true)
        if(previouslyReserved&&!liveEntry&&!locateUnissued){
            val f=frame();val e=capture("darts_readonly_review")
            if(already(f.text)&&f.text.contains("ダーツ"))set("darts",CheckinState.ALREADY_DONE,evidence=e)
            else set("darts",CheckinState.UNCONFIRMED,"prior_darts_submission_no_reopen",e)
            return
        }
        val deadline=SystemClock.elapsedRealtime()+70_000
        var selected=liveEntry
        while(SystemClock.elapsedRealtime()<deadline&&!selected){
            val f=frame()
            if(Regex("ログインしてください|会員登録・ログイン|ログインする").containsMatchIn(f.text))error("member_login_required")
            val height=ui.svc.resources.displayMetrics.heightPixels
            val banner=f.lines.filter{it.top<height*.33&&it.top>height*.07}
            if(CheckinReceiptPolicy.dartsEntryCandidate(banner.joinToString(" "){it.text})){
                val e=capture("darts_banner")
                val line=banner.firstOrNull{it.text.contains("毎日")||it.text.contains("ダーツ")}
                if(line!=null){
                    // Pixel revalidation may reject an animating carousel. That is safe to retry before entry.
                    val fresh=ui.png()
                    if(CheckinOcr.stable(f.png,fresh,line)){
                        val reviewing=reserved("darts")
                        if(!reviewing)check(mutate {store.reserve("darts",run,date)}){"darts_already_reserved"}
                        if(ui.click(f,line)){selected=true;store.log(run,"DARTS_ENTRY evidence=$e review=$reviewing")}
                    }
                }
            }
            if(!selected)delay(600)
        }
        check(selected){"free_darts_carousel_entry_not_stable"}
        var f=awaitFrame("darts_start"){already(it.text)||CheckinReceiptPolicy.boundDartsStartPage(ui.nativeText(),true)};val e=capture("darts_page")
        if(already(f.text)){set("darts",CheckinState.ALREADY_DONE,evidence=e);return}
        if(store.mayLocateUnissuedDartsStart(date)){
            check(ui.nativeLabel("スタート")!=null&&mutate {store.reconcileUnissuedDartsStart(run,date,true)}){"unissued_darts_start_page_not_verified"}
        }
        if(paid(f.text))error("paid_or_ineligible_game")
        check(CheckinReceiptPolicy.boundDartsStartPage(ui.nativeText(),true)){"current_free_darts_rules_not_confirmed"}
        // Prior UNCONFIRMED runs can inspect official results, but may never submit again.
        check(ui.nativeLabel("スタート")!=null){"darts_start_control_not_recognized"}
        check(submitNative("darts","スタート")){"darts_start_changed_before_dispatch"}
        dartsResult()
    }
    private suspend fun dartsResult(initial:CheckinFrame?=null){
        val until=SystemClock.elapsedRealtime()+45_000
        var pending=initial
        while(SystemClock.elapsedRealtime()<until){
            delay(1000);val f=pending?:frame();pending=null
            val pull=if(f.text.contains("ダーツ"))CheckinOcr.dartPull(f.png,f.lines)else null
            if(pull!=null&&!store.read("darts",date)!!.optBoolean("pullSubmitted")){
                capture("darts_pull_ready")
                check(ui.pullDart(f,pull)){"darts_pull_changed_or_rejected_no_replay"}
                continue
            }
            val receipt=Regex("肉マネー|クーポン").containsMatchIn(f.text)&&CheckinReceiptPolicy.realReceipt(true,false,true,f.text)
            if(receipt){
                val amount=CheckinReceiptPolicy.rewardAmount(f.lines,"円")
                val proof=capture("darts_reward")
                set("darts",CheckinState.SUCCESS,"official_darts_result",proof,"肉マネークーポン（円）",amount,false);return
            }
            if(already(f.text)){set("darts",CheckinState.ALREADY_DONE,evidence=capture("darts_done"));return}
        }
        set("darts",CheckinState.UNCONFIRMED,"darts_reward_not_confirmed",capture("darts_unknown"))
    }

    // ---- TikTok Lite: 14-day check-in, yesterday's unlocked reward, today's unlock ----
    private val screenHeight get()=ui.svc.resources.displayMetrics.heightPixels
    /** Taps stay in the upper part of the page, clear of the floating bottom campaign banner. */
    private fun reachable(l:CheckinLine?)=l!=null&&l.bottom<screenHeight*.78&&l.top>screenHeight*.1&&TikTokCheckinPolicy.safeTarget(l)
    private fun closeX(f:CheckinFrame,fromY:Int):Pair<Int,Int>? {
        val bmp=android.graphics.BitmapFactory.decodeByteArray(f.png,0,f.png.size)?:return null
        try{
            val w=bmp.width;val h=bmp.height
            return TikTokCheckinPolicy.closeButton({x,y->if(x<0||y<0||x>=w||y>=h)0 else {val c=bmp.getPixel(x,y);((c shr 16 and 255)*299+(c shr 8 and 255)*587+(c and 255)*114)/1000}},
                w,h,fromY,h-120,f.lines.filter{!TikTokCheckinPolicy.safeTarget(it)})
        }finally{bmp.recycle()}
    }
    /** OK when the popup has one, otherwise the round ✕ under it. Never a popup's own action button. */
    private suspend fun tiktokClosePopup(f:CheckinFrame,fromY:Int):Boolean {
        val ok=TikTokCheckinPolicy.okButton(f.lines)
        if(ok!=null&&ui.click(f,ok)){delay(900);return true}
        val (x,y)=closeX(f,fromY)?:return false
        if(!ui.click(f,CheckinLine("close",x-20,y-20,x+20,y+20)))return false
        delay(900);return true
    }
    private suspend fun tiktokSettle(start:CheckinFrame,allowBack:Boolean=false):CheckinFrame {
        var f=start
        var backTried=false
        repeat(4){
            val top=(screenHeight*.2).toInt()
            if(!TikTokCheckinPolicy.popup(f.lines))return f
            if(TikTokCheckinPolicy.okButton(f.lines)==null&&closeX(f,top)==null){
                // 11/13 07:30: the 14日連続 popup over the feed had no ✕ the pixel finder could see and hid the ギフト tab for
                // 90 s. One system Back is tried (no popup button is ever pressed); if it stays, the old path goes on.
                if(!allowBack||backTried||!TikTokCheckinPolicy.backDismissible(f.lines))return f
                backTried=true;capture("tiktok_popup_back");ui.back();f=frame();return@repeat
            }
            capture("tiktok_popup_close")
            if(!tiktokClosePopup(f,top))delay(800)
            f=frame()
        }
        return f
    }
    private suspend fun tiktokTop(){repeat(6){ui.swipe((screenHeight*.21).toInt(),(screenHeight*.88).toInt(),300)}}
    private suspend fun tiktokGift():CheckinFrame {
        val until=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<until){
            val raw=frame()
            val f=if(TikTokCheckinPolicy.giftPage(raw.lines))raw else tiktokSettle(raw,allowBack=true)
            if(TikTokCheckinPolicy.giftPage(f.lines)){tiktokTop();return tiktokSettle(frame())}
            val tab=TikTokCheckinPolicy.giftTab(f.lines,screenHeight)
            if(tab!=null&&ui.click(f,tab))delay(4000) else delay(1500)
        }
        capture("tiktok_gift_page_load_timeout");error("tiktok_gift_page_load_timeout")
    }
    /** Scrolls down the gift page until a card is in reach; one second pass from the top. */
    private suspend fun tiktokSeek(label:String,found:(List<CheckinLine>)->Boolean):CheckinFrame? {
        repeat(2){pass->
            if(pass==1)tiktokTop()
            repeat(10){
                val f=tiktokSettle(frame())
                if(found(f.lines))return f
                ui.swipe((screenHeight*.72).toInt(),(screenHeight*.42).toInt(),900)
            }
        }
        capture("tiktok_"+label+"_not_found");return null
    }
    private suspend fun tiktok(){
        if(TikTokCheckinPolicy.TASKS.all{done(it)})return
        val gift=try{tiktokGift()}catch(e:CancellationException){throw e}catch(e:CheckinPerceptionUnavailable){throw e}catch(e:Exception){
            val why=e.message?.take(160).orEmpty()
            TikTokCheckinPolicy.TASKS.forEach{t->if(!done(t))set(t,if(reserved(t))CheckinState.UNCONFIRMED else CheckinState.BLOCKED,why)};return
        }
        store.log(run,"TIKTOK_BALANCE before="+TikTokCheckinPolicy.balance(gift.lines))
        task("checkin"){tiktokCheckin()}
        task("claim"){tiktokClaim()}
        task("unlock"){tiktokUnlock()}
        try{tiktokTop();val f=tiktokSettle(frame());store.log(run,"TIKTOK_BALANCE after="+TikTokCheckinPolicy.balance(f.lines));capture("tiktok_balance_after")}
        catch(e:CancellationException){throw e}catch(e:CheckinPerceptionUnavailable){throw e}catch(_:Exception){}
    }
    private suspend fun tiktokCheckin(){
        set("checkin",CheckinState.RUNNING)
        val f=tiktokSeek("checkin"){TikTokCheckinPolicy.checkinDone(it)||reachable(TikTokCheckinPolicy.checkinButton(it))}?:error("checkin_card_not_found")
        if(TikTokCheckinPolicy.checkinDone(f.lines)){set("checkin",CheckinState.ALREADY_DONE,"official_checkin_done_today",capture("tiktok_checkin_done"));return}
        if(reserved("checkin")){set("checkin",CheckinState.UNCONFIRMED,"prior_checkin_submission_no_replay",capture("tiktok_checkin_review"));return}
        val button=TikTokCheckinPolicy.checkinButton(f.lines)!!
        check(mutate {store.reserve("checkin",run,date)}){"already_reserved"}
        check(ui.click(f,button){mutate {store.submitted("checkin",run,date,preflightVerified=true)}}){"checkin_button_changed"}
        val until=SystemClock.elapsedRealtime()+20_000
        while(SystemClock.elapsedRealtime()<until){
            delay(1200);val r=frame()
            if(TikTokCheckinPolicy.checkinReceipt(r.lines)){
                val amount=TikTokCheckinPolicy.receiptAmount(r.lines)
                set("checkin",CheckinState.SUCCESS,"official_checkin_receipt",capture("tiktok_checkin_receipt"),"TikTokポイント",amount,amount!=null)
                tiktokSettle(r);return
            }
            if(TikTokCheckinPolicy.checkinDone(r.lines)){set("checkin",CheckinState.SUCCESS,"official_checkin_card_done_after_tap",capture("tiktok_checkin_card_done"),"TikTokポイント");return}
        }
        // 11/12 07:31: the receipt popup was not recognised, yet the card already read 明日チェックインして…; read the card itself
        // (popups closed, no tap on it) before calling the check-in unconfirmed. The button is never pressed again.
        val card=tiktokSeek("checkin_verify"){TikTokCheckinPolicy.checkinDone(it)||TikTokCheckinPolicy.checkinButton(it)!=null}
        if(card!=null&&TikTokCheckinPolicy.checkinDone(card.lines)){
            set("checkin",CheckinState.SUCCESS,"official_checkin_card_done_after_tap",capture("tiktok_checkin_card_done"),"TikTokポイント");return
        }
        set("checkin",CheckinState.UNCONFIRMED,"checkin_receipt_not_seen",capture("tiktok_checkin_unconfirmed"))
    }
    private suspend fun tiktokClaim(){
        set("claim",CheckinState.RUNNING)
        val f=tiktokSeek("claim"){reachable(TikTokCheckinPolicy.claimButton(it))||TikTokCheckinPolicy.unlockTitle(it)!=null||TikTokCheckinPolicy.unlockDone(it)}
            ?:error("claim_area_not_found")
        val button=TikTokCheckinPolicy.claimButton(f.lines)
        if(button==null&&TikTokCheckinPolicy.claimTitle(f.lines)==null){set("claim",CheckinState.ALREADY_DONE,"no_claimable_reward_card_today",capture("tiktok_claim_absent"));return}
        check(button!=null&&reachable(button)){"claim_button_not_in_reach"}
        if(reserved("claim")){set("claim",CheckinState.UNCONFIRMED,"prior_claim_submission_no_replay",capture("tiktok_claim_review"));return}
        val amount=TikTokCheckinPolicy.claimAmount(f.lines);capture("tiktok_claim_card")
        check(mutate {store.reserve("claim",run,date)}){"already_reserved"}
        check(ui.click(f,button!!){mutate {store.submitted("claim",run,date,preflightVerified=true)}}){"claim_button_changed"}
        val until=SystemClock.elapsedRealtime()+20_000
        while(SystemClock.elapsedRealtime()<until){
            delay(1500);val r=tiktokSettle(frame())
            if(TikTokCheckinPolicy.claimTitle(r.lines)==null&&TikTokCheckinPolicy.giftPage(r.lines)||TikTokCheckinPolicy.claimTitle(r.lines)==null&&(TikTokCheckinPolicy.unlockTitle(r.lines)!=null||TikTokCheckinPolicy.checkinTitle(r.lines)!=null)){
                set("claim",CheckinState.SUCCESS,"official_claim_card_removed_after_tap",capture("tiktok_claim_done"),"TikTokポイント",amount,amount!=null);return
            }
        }
        set("claim",CheckinState.UNCONFIRMED,"claim_card_still_present",capture("tiktok_claim_unconfirmed"))
    }
    private suspend fun tiktokUnlock(){
        set("unlock",CheckinState.RUNNING)
        val f=tiktokSeek("unlock"){TikTokCheckinPolicy.unlockDone(it)||reachable(TikTokCheckinPolicy.unlockButton(it))}?:error("unlock_card_not_found")
        val button=TikTokCheckinPolicy.unlockButton(f.lines)
        if(TikTokCheckinPolicy.unlockDone(f.lines)&&button==null){set("unlock",CheckinState.ALREADY_DONE,"official_unlock_done_today",capture("tiktok_unlock_done"));return}
        if(reserved("unlock")){set("unlock",CheckinState.UNCONFIRMED,"prior_unlock_submission_no_replay",capture("tiktok_unlock_review"));return}
        check(button!=null&&reachable(button)){"unlock_button_not_in_reach"}
        check(mutate {store.reserve("unlock",run,date)}){"already_reserved"}
        check(ui.click(f,button!!){mutate {store.submitted("unlock",run,date,preflightVerified=true)}}){"unlock_button_changed"}
        val until=SystemClock.elapsedRealtime()+20_000
        while(SystemClock.elapsedRealtime()<until){
            delay(1200);val r=frame()
            if(TikTokCheckinPolicy.unlockPopup(r.lines)){
                set("unlock",CheckinState.SUCCESS,"official_unlock_confirmed_reward_tomorrow",capture("tiktok_unlock_popup"))
                tiktokSettle(r);return
            }
            if(TikTokCheckinPolicy.unlockDone(r.lines)){set("unlock",CheckinState.SUCCESS,"official_unlock_card_done_after_tap",capture("tiktok_unlock_card_done"));return}
        }
        set("unlock",CheckinState.UNCONFIRMED,"unlock_result_not_seen",capture("tiktok_unlock_unconfirmed"))
    }
}
