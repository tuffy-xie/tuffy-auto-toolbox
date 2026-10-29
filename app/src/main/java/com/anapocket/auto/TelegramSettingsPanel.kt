package com.anapocket.auto

import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class TelegramSettingsPanel(private val activity: AppCompatActivity, private val scope: CoroutineScope) {
    fun bind() {
        val store = TelegramSettingsStore(activity)
        val enabled = activity.findViewById<SwitchCompat>(R.id.swTelegram)
        val name = activity.findViewById<EditText>(R.id.etTelegramName)
        val token = activity.findViewById<EditText>(R.id.etTelegramToken)
        val chat = activity.findViewById<EditText>(R.id.etTelegramChat)
        val buttons = listOf(R.id.btnTelegramSave, R.id.btnTelegramIdentify, R.id.btnTelegramTest, R.id.btnTelegramClear).map { activity.findViewById<Button>(it) }
        fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
        val loaded = runCatching { store.load() }.getOrDefault(TelegramConfig())
        enabled.isChecked = loaded.enabled; name.setText(loaded.botName); chat.setText(loaded.chatId)
        token.hint = if (loaded.token.isNotEmpty()) "Token已加密保存；留空保留" else "BotFather提供的Token"
        fun config(needsChat: Boolean): TelegramConfig {
            val secret = token.text.toString().trim().ifEmpty { store.load().token }
            require(TelegramPolicy.tokenValid(secret)) { "请填写有效的Bot Token" }
            val target = chat.text.toString().trim()
            require(target.isEmpty() || TelegramPolicy.chatValid(target)) { "Chat ID须为数字或频道@用户名" }
            require(!needsChat || TelegramPolicy.chatValid(target)) { "请先识别接收人或填写Chat ID" }
            return TelegramConfig(enabled.isChecked, name.text.toString().trim(), secret, target)
        }
        fun saved(c: TelegramConfig) {
            store.save(c); token.setText(""); token.hint = "Token已加密保存；留空保留"
        }
        var reverting = false
        enabled.setOnCheckedChangeListener { _, checked ->
            if (reverting) return@setOnCheckedChangeListener
            try {
                if (checked) saved(config(true)) else store.disable()
                store.status(if (checked) "通知已开启并保存" else "通知已关闭并保存")
            } catch (_: Exception) {
                reverting = true; enabled.isChecked = !checked; reverting = false
                store.status("开关未保存，请先填写Token并识别接收人")
                toast("请先填写Token并识别接收人，再开启通知")
            }
        }
        buttons[0].setOnClickListener {
            try { val c = config(enabled.isChecked); saved(c); store.status("配置已保存"); toast("Telegram配置已保存") }
            catch (e: IllegalArgumentException) { toast(e.message ?: "配置无效") }
            catch (_: Exception) { toast("保存失败，请重新填写Token") }
        }
        buttons[1].setOnClickListener {
            val c = try { config(false) } catch (e: Exception) { toast(if(e is IllegalArgumentException) e.message ?: "配置无效" else "Token读取失败"); return@setOnClickListener }
            buttons.forEach { it.isEnabled = false }; store.status("正在连接机器人并识别接收人")
            scope.launch {
                try {
                    val (actualName, target) = TelegramClient().identify(c)
                    name.setText("@" + actualName); chat.setText(target)
                    saved(c.copy(enabled = enabled.isChecked, botName = actualName, chatId = target))
                    store.status("已连接 @$actualName · 接收Chat ID：$target")
                    toast("接收人已识别，可点测试推送")
                } catch (e: IllegalStateException) { store.status(e.message ?: "连接失败") }
                catch (_: Exception) { store.status("连接失败，请检查Token和网络") }
                finally { buttons.forEach { it.isEnabled = true } }
            }
        }
        buttons[2].setOnClickListener {
            val c = try { config(true).also { saved(it) } } catch (e: Exception) { toast(if(e is IllegalArgumentException) e.message ?: "配置无效" else "配置保存失败"); return@setOnClickListener }
            buttons.forEach { it.isEnabled = false }; store.status("正在发送测试消息")
            scope.launch {
                try {
                    TelegramClient().identify(c)
                    val r = TelegramClient().send(c, "Tuffy的自动百宝箱：测试推送成功。\n" + TokyoDate.time() + " JST")
                    store.status(if (r.ok) "测试推送成功 · " + TokyoDate.time() else if(r.uncertain) "测试结果未确认，请检查Telegram；未自动重发" else "测试失败，请检查Chat ID、机器人权限和网络")
                } catch (_: Exception) { store.status("测试失败，请检查Token、Bot用户名和网络") }
                finally { buttons.forEach { it.isEnabled = true } }
            }
        }
        buttons[3].setOnClickListener {
            store.clear(); enabled.isChecked = false; name.setText(""); token.setText(""); chat.setText("")
            token.hint = "BotFather提供的Token"; store.status("配置已清除")
        }
        activity.findViewById<TextView>(R.id.tvTelegramStatus).text = store.status()
    }
}
