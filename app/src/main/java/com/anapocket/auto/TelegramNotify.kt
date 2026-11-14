package com.anapocket.auto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

/** Token never appears in app settings snapshots, logs, worker Data or exported task evidence. */
class TelegramSettingsStore(private val ctx: Context, private val namespace: String = "ana_telegram_v1") {
    private val pref get() = ctx.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "com.anapocket.auto.$namespace"
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): TelegramConfig {
        val encoded = pref.getString("encrypted_token", "").orEmpty()
        val token = if (encoded.isEmpty()) "" else {
            val pair = encoded.split(":", limit = 2)
            check(pair.size == 2) { "telegram_secret_unavailable" }
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(pair[0], Base64.NO_WRAP)))
                String(doFinal(Base64.decode(pair[1], Base64.NO_WRAP)), Charsets.UTF_8)
            }
        }
        return TelegramConfig(pref.getBoolean("enabled", false), pref.getString("bot_name", "").orEmpty(), token, pref.getString("chat_id", "").orEmpty())
    }
    fun save(c: TelegramConfig) {
        require(c.token.isBlank() || TelegramPolicy.tokenValid(c.token))
        require(c.chatId.isBlank() || TelegramPolicy.chatValid(c.chatId))
        require(!c.enabled || TelegramPolicy.tokenValid(c.token) && TelegramPolicy.chatValid(c.chatId))
        val encrypted = if (c.token.isEmpty()) "" else Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key())
            val cipher = doFinal(c.token.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher, Base64.NO_WRAP)
        }
        check(pref.edit().putBoolean("enabled", c.enabled).putString("bot_name", c.botName)
            .putString("chat_id", c.chatId).putString("encrypted_token", encrypted).commit())
    }
    fun disable() { check(pref.edit().putBoolean("enabled", false).commit()) }
    fun clear() { check(pref.edit().clear().commit()) }
    fun status(text: String) { pref.edit().putString("last_status", text).apply() }
    fun status() = pref.getString("last_status", "尚未连接").orEmpty()
}

data class TelegramReply(val ok: Boolean, val code: Int, val body: JSONObject? = null, val uncertain: Boolean = false)
fun interface TelegramTransport { suspend fun call(token: String, method: String, body: JSONObject): TelegramReply }
class TelegramHttps : TelegramTransport {
    override suspend fun call(token: String, method: String, body: JSONObject): TelegramReply = withContext(Dispatchers.IO) {
        require(TelegramPolicy.tokenValid(token))
        require(method in setOf("getMe", "getUpdates", "sendMessage"))
        var conn: HttpsURLConnection? = null
        try {
            conn = URL("https://api.telegram.org/bot" + token + "/" + method).openConnection() as HttpsURLConnection
            conn.requestMethod = "POST"; conn.connectTimeout = 10_000; conn.readTimeout = 15_000
            conn.instanceFollowRedirects = false; conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val out = StringBuilder(); val buffer = CharArray(2048)
                while (out.length <= 262144) { val n = reader.read(buffer); if(n < 0) break; out.append(buffer, 0, n) }
                if (out.length > 262144) "" else out.toString()
            }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            TelegramReply(code in 200..299 && json?.optBoolean("ok") == true, code, json,
                uncertain = json == null || !json.has("ok"))
        } catch (_: Exception) {
            // Exception messages can include the full authenticated URL. Never log them.
            TelegramReply(false, 0, uncertain = true)
        } finally { conn?.disconnect() }
    }
}
class TelegramClient(private val transport: TelegramTransport = TelegramHttps()) {
    suspend fun identify(c: TelegramConfig): Pair<String, String> {
        val me = transport.call(c.token, "getMe", JSONObject())
        check(me.ok) { "连接失败：请检查Token和网络" }
        val user = me.body?.optJSONObject("result") ?: error("机器人信息无效")
        val name = user.optString("username")
        check(user.optBoolean("is_bot") && TelegramPolicy.matchesBot(c.botName, name)) { "Bot用户名与Token不匹配" }
        if (c.chatId.isNotBlank()) return name to c.chatId
        val updates = transport.call(c.token, "getUpdates", JSONObject().put("timeout", 0).put("limit", 100))
        check(updates.ok) { "无法识别接收人；已有Webhook时请手动填写Chat ID" }
        val list = updates.body?.optJSONArray("result") ?: JSONArray()
        val starts = mutableListOf<String>()
        for(i in 0 until list.length()) {
            val msg = list.optJSONObject(i)?.optJSONObject("message") ?: continue
            val chat = msg.optJSONObject("chat") ?: continue
            if (chat.optString("type") == "private" && Regex("^/start(?:@[A-Za-z0-9_]+)?(?:\\s.*)?$").matches(msg.optString("text"))) starts.add(chat.optString("id"))
        }
        val target = TelegramPolicy.privateChat(starts)
        check(target != null && TelegramPolicy.chatValid(target)) { "请先给机器人发/start；多个私聊请手动填写Chat ID" }
        return name to target
    }
    suspend fun send(c: TelegramConfig, text: String): TelegramReply {
        require(TelegramPolicy.tokenValid(c.token) && TelegramPolicy.chatValid(c.chatId))
        require(text.length in 1..1000)
        return transport.call(c.token, "sendMessage", JSONObject().put("chat_id", c.chatId).put("text", text)
            .put("link_preview_options", JSONObject().put("is_disabled", true)))
    }
}
/** Durable send reservation: process death after dispatch cannot resend a possibly delivered result. */
class TelegramDeliveryLedger(ctx: Context, namespace: String = "ana_telegram_delivery_v1") {
    private val pref = ctx.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    fun state(id: String) = pref.getString(id, "").orEmpty()
    fun set(id: String, state: String) { check(pref.edit().putString(id, state).commit()) }
    fun begin(id: String): Boolean = synchronized(TelegramDeliveryLedger::class.java) {
        if (!TelegramPolicy.mayBeginDelivery(state(id))) false else { set(id, "sending"); true }
    }
}
object TelegramNotifier {
    fun enqueue(ctx: Context, id: String, text: String, checkinApp:String="", checkinGeneration:Long=-1, checkinDate:String="") {
        try {
            val store = TelegramSettingsStore(ctx)
            val c = store.load()
            if (!c.enabled || !TelegramPolicy.tokenValid(c.token) || !TelegramPolicy.chatValid(c.chatId)) return
            val event = id + "_" + TelegramPolicy.fingerprint(c)
            if (!TelegramPolicy.mayBeginDelivery(TelegramDeliveryLedger(ctx).state(event))) return
            val request = OneTimeWorkRequestBuilder<TelegramWorker>()
                .setInputData(workDataOf("text" to text.take(1000), "target" to TelegramPolicy.fingerprint(c), "event" to event,
                    "checkinApp" to checkinApp,"checkinGeneration" to checkinGeneration,"checkinDate" to checkinDate))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("telegram_" + id, ExistingWorkPolicy.KEEP, request)
            store.status("结果已排队，等待推送")
        } catch (_: Exception) { Logger.w("TELEGRAM", "notification enqueue failed; credentials not logged") }
    }
}
class TelegramWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = TelegramSettingsStore(applicationContext)
        val c = try { store.load() } catch (_: Exception) { store.status("Token无法读取，请重新保存"); return Result.failure() }
        if (!c.enabled || TelegramPolicy.fingerprint(c) != inputData.getString("target")) return Result.success()
        val text = inputData.getString("text") ?: return Result.failure()
        val event = inputData.getString("event") ?: return Result.failure()
        // An old P1 delivery cannot announce a disabled/new-generation task as a new fault.
        if(event.startsWith("checkin_")){
            val app=CheckinApp.from(inputData.getString("checkinApp"))?:return Result.success()
            val config=CheckinStore(applicationContext,app).config()
            if(!CheckinDispatchPolicy.accepts(config.enabled,config.generation,inputData.getLong("checkinGeneration",-1),
                inputData.getString("checkinDate")==TokyoDate.of()))return Result.success()
        }
        val ledger = TelegramDeliveryLedger(applicationContext)
        if (!ledger.begin(event)) {
            if (ledger.state(event) == "sending") { ledger.set(event, "uncertain"); store.status("上次发送中断，结果未确认；未自动重复发送") }
            return Result.success()
        }
        val reply = try { TelegramClient().send(c, text) } catch (_: Exception) { ledger.set(event, "failed"); store.status("配置无效，请重新保存"); return Result.failure() }
        return when {
            reply.ok -> { ledger.set(event, "sent"); store.status("推送成功 · " + TokyoDate.time()); Result.success() }
            TelegramPolicy.retry(runAttemptCount, reply.body?.optBoolean("ok", true) == false && !reply.uncertain, reply.code) -> {
                ledger.set(event, "retry"); store.status("服务暂不可用，将有限重试"); Result.retry()
            }
            else -> { ledger.set(event, if (reply.uncertain) "uncertain" else "failed"); store.status(if (reply.uncertain) "发送结果未确认，请检查Telegram；未自动重复发送" else "推送失败，请检查Token、Chat ID和机器人权限"); Result.failure() }
        }
    }
}
