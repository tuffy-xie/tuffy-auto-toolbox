package com.anapocket.auto
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Controlled fixtures only. Never accesses the user's Telegram config or sends a network request. */
object TelegramSelfTest {
    suspend fun run(ctx: Context) {
        val results=JSONArray()
        val store=TelegramSettingsStore(ctx,"telegram_controlled_selftest_v1")
        fun verify(name:String, ok:Boolean) {results.put(JSONObject().put("test",name).put("pass",ok));check(ok){name}}
        var error=""
        try {
            val delivery=TelegramDeliveryLedger(ctx,"telegram_controlled_delivery_v1")
            delivery.set("fixture", "")
            verify("send_reservation_persisted",delivery.begin("fixture") && delivery.state("fixture")=="sending")
            verify("interrupted_send_not_repeated",!TelegramDeliveryLedger(ctx,"telegram_controlled_delivery_v1").begin("fixture"))
            delivery.set("fixture","retry");verify("explicit_rejection_may_retry",delivery.begin("fixture"))
            delivery.set("fixture","sent");verify("delivered_event_not_repeated",!delivery.begin("fixture"))
            val token="123456789:abcdefghijklmnopqrstuvwxyz_123456789"
            val c=TelegramConfig(true,"test_bot",token,"123456789")
            store.clear();store.save(c)
            verify("keystore_round_trip",store.load()==c)
            store.disable();verify("disable_preserves_encrypted_credentials",store.load()==c.copy(enabled=false));store.save(c)
            verify("token_not_in_preferences_plaintext",ctx.getSharedPreferences("telegram_controlled_selftest_v1",0).all.values.none {it.toString().contains(token)})
            val one=JSONObject().put("message",JSONObject().put("text","/start").put("chat",JSONObject().put("type","private").put("id",123456789)))
            val group=JSONObject().put("message",JSONObject().put("text","/start").put("chat",JSONObject().put("type","group").put("id",-444)))
            val sent=mutableListOf<JSONObject>()
            val api=TelegramClient(TelegramTransport { got, method, body ->
                verify("request_token_shape_"+method,got==token)
                val result:Any=when(method){
                    "getMe" -> JSONObject().put("is_bot",true).put("username","test_bot")
                    "getUpdates" -> JSONArray().put(group).put(one)
                    "sendMessage" -> {sent.add(body);JSONObject().put("message_id",42)}
                    else -> error("unexpected_method")
                }
                TelegramReply(true,200,JSONObject().put("ok",true).put("result",result))
            })
            verify("private_start_target_and_group_ignored",api.identify(c.copy(chatId="")).second=="123456789")
            verify("wrong_bot_rejected",runCatching{api.identify(c.copy(botName="wrong_bot"))}.isFailure)
            val text=TelegramText.daily("2026-10-28",25,21,21,0,4,21,21,"达到每日25次启动上限")
            verify("controlled_send_receipt",api.send(c,text).ok)
            verify("exact_target_and_concise_body",sent.size==1 && sent[0].getString("chat_id")==c.chatId && sent[0].getString("text")==text && text.lines().size==5)
            verify("token_absent_from_payload",!sent[0].toString().contains(token))
            val rejected=TelegramClient(TelegramTransport{_,_,_->TelegramReply(false,403,JSONObject().put("ok",false))})
            verify("api_rejection_not_success",!rejected.send(c,text).ok)
            val uncertain=TelegramClient(TelegramTransport{_,_,_->TelegramReply(false,0,uncertain=true)})
            verify("lost_reply_no_retry",uncertain.send(c,text).uncertain && !TelegramPolicy.retry(0,false,0))
            val ambiguous=TelegramClient(TelegramTransport{_,method,_->if(method=="getMe") TelegramReply(true,200,JSONObject().put("result",JSONObject().put("is_bot",true).put("username","test_bot"))) else TelegramReply(true,200,JSONObject().put("result",JSONArray().put(one).put(JSONObject().put("message",JSONObject().put("text","/start payload").put("chat",JSONObject().put("type","private").put("id",987654321))))))})
            verify("multiple_private_recipients_not_auto_selected",runCatching{ambiguous.identify(c.copy(chatId=""))}.isFailure)
        } catch(e:Exception) {error=e.javaClass.simpleName}
        finally {store.clear()}
        val report=JSONObject().put("environment","controlled_android_fixture_no_external_messages").put("productionSettingsUntouched",true)
            .put("results",results).put("error",error).put("pass",error.isEmpty())
        Evidence.write(ctx,"telegram_controlled_selftest_"+System.currentTimeMillis()+".json",report.toString(2))
        Logger.i("TELEGRAM_TEST","controlled test done pass="+error.isEmpty()+" checks="+results.length())
    }
}
