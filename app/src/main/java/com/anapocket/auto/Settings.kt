package com.anapocket.auto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 四种移动方式。jp 为 App 内日文原文，defaultKm 为默认最大接取公里数。 */
enum class Cat(val jp: String, val defaultKm: Int) {
    MOV_ANY("移動手段問わず", 40),
    WALK("徒歩", 5),
    BIKE("自転車", 5),
    CAR("車", 40),
    SHINKANSEN("新幹線", 0),
    FLIGHT("飛行機", 0);

    val longDistance:Boolean get() = this==SHINKANSEN || this==FLIGHT
    val title:String get() = when(this){MOV_ANY->"移动不限";WALK->"徒步";BIKE->"自行车";CAR->"汽车";SHINKANSEN->"新干线";FLIGHT->"飞机"}
    val maxKm:Int get() = if(longDistance)20000 else 200

    companion object {
        fun ofName(n: String?): Cat? = values().firstOrNull { it.name == n }
        fun ofJp(s: String?): Cat? = values().firstOrNull { it.jp == s }
    }
}

object Dates {
    fun of(offset: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    fun today(): String = of(0)
    fun tomorrow(): String = of(1)
}

/**
 * 全部用户设置。持久化在 DataStore（Preferences），
 * 不写死在 Kotlin 里：公里数、目标次数、日期选择都由 UI 配置。
 */
data class AppSettings(
    val enabled: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 5,

    val adEnabled: Boolean = true,
    /** 兼容旧配置字段，但固定为 25，不再由 UI 修改。 */
    val adTarget: Int = DAILY_AD_LIMIT,
    val adNotify: Boolean = true,

    val dayToday: Boolean = true,
    val dayTomorrow: Boolean = false,

    val catEnabled: Map<String, Boolean> = valuesMap { !it.longDistance },
    val catKm: Map<String, Int> = valuesMap { it.defaultKm },

    val notifySummary: Boolean = true,

    /** 默认关闭：仅在节点不可读时，用「位置猜测 + 详情页强校验」作为兜底。 */
    val allowPositionalFallback: Boolean = false
) {
    fun catOn(c: Cat): Boolean = catEnabled[c.name] ?: !c.longDistance
    fun kmOf(c: Cat): Int = (catKm[c.name] ?: c.defaultKm).let{if(c.longDistance && it==0)0 else it.coerceIn(1,c.maxKm)}

    fun toJson(): String {
        val o = JSONObject()
        o.put("enabled", enabled)
        o.put("hour", hour)
        o.put("minute", minute)
        o.put("adEnabled", adEnabled)
        o.put("adTarget", DAILY_AD_LIMIT)
        o.put("adNotify", adNotify)
        o.put("dayToday", dayToday)
        o.put("dayTomorrow", dayTomorrow)
        o.put("notifySummary", notifySummary)
        o.put("allowPositionalFallback", allowPositionalFallback)
        val ce = JSONObject()
        val ck = JSONObject()
        for (c in Cat.values()) {
            ce.put(c.name, catOn(c))
            ck.put(c.name, kmOf(c))
        }
        o.put("catEnabled", ce)
        o.put("catKm", ck)
        return o.toString()
    }

    companion object {
        private fun <T> valuesMap(v: (Cat) -> T): Map<String, T> {
            val m = LinkedHashMap<String, T>()
            for (c in Cat.values()) m[c.name] = v(c)
            return m
        }

        fun fromJson(s: String?): AppSettings {
            if (s.isNullOrBlank()) return AppSettings()
            return try {
                val o = JSONObject(s)
                val d = AppSettings()
                val ce = o.optJSONObject("catEnabled")
                val ck = o.optJSONObject("catKm")
                val en = LinkedHashMap<String, Boolean>()
                val km = LinkedHashMap<String, Int>()
                for (c in Cat.values()) {
                    en[c.name] = ce?.optBoolean(c.name, d.catOn(c)) ?: d.catOn(c)
                    km[c.name] = ck?.optInt(c.name, d.kmOf(c)) ?: d.kmOf(c)
                }
                AppSettings(
                    enabled = o.optBoolean("enabled", d.enabled),
                    hour = o.optInt("hour", d.hour),
                    minute = o.optInt("minute", d.minute),
                    adEnabled = o.optBoolean("adEnabled", d.adEnabled),
                    adTarget = DAILY_AD_LIMIT,
                    adNotify = o.optBoolean("adNotify", d.adNotify),
                    dayToday = o.optBoolean("dayToday", d.dayToday),
                    dayTomorrow = o.optBoolean("dayTomorrow", d.dayTomorrow),
                    catEnabled = en,
                    catKm = km,
                    notifySummary = o.optBoolean("notifySummary", d.notifySummary),
                    allowPositionalFallback = o.optBoolean("allowPositionalFallback", d.allowPositionalFallback)
                )
            } catch (t: Throwable) {
                AppSettings()
            }
        }
    }
}

private val Context.settingStore: DataStore<Preferences> by preferencesDataStore(name = "ana_settings_v9")

object Prefs {
    private val KEY = stringPreferencesKey("json")

    suspend fun load(ctx: Context): AppSettings {
        return loadFromStore(ctx.settingStore)
    }

    suspend fun save(ctx: Context, s: AppSettings) {
        TravelSelection.mutex.lock();try{ctx.settingStore.edit { it[KEY] = s.toJson() }}finally{TravelSelection.mutex.unlock()}
        Evidence.write(ctx, "settings_snapshot.json", s.toJson())
    }

    internal suspend fun loadFromStore(store:DataStore<Preferences>)=AppSettings.fromJson(store.data.first()[KEY])
    suspend fun setCategory(ctx:Context,cat:Cat,on:Boolean):AppSettings {
        val result=setCategoryInStore(ctx.settingStore,cat,on)
        Evidence.write(ctx,"settings_snapshot.json",result.toJson())
        Logger.i("SELECTION","cat=$cat selected=$on km="+result.kmOf(cat))
        return result
    }
    suspend fun toggleCategory(ctx:Context,cat:Cat):AppSettings {
        val result=toggleCategoryInStore(ctx.settingStore,cat)
        Evidence.write(ctx,"settings_snapshot.json",result.toJson())
        Logger.i("SELECTION","cat=$cat selected="+result.catOn(cat)+" km="+result.kmOf(cat))
        return result
    }
    internal suspend fun toggleCategoryInStore(store:DataStore<Preferences>,cat:Cat)=updateCategoryInStore(store,cat,null)
    internal suspend fun setCategoryInStore(store:DataStore<Preferences>,cat:Cat,on:Boolean)=updateCategoryInStore(store,cat,on)
    private suspend fun updateCategoryInStore(store:DataStore<Preferences>,cat:Cat,on:Boolean?):AppSettings {
        var result=AppSettings()
        TravelSelection.mutex.lock();try{store.edit { p->result=AppSettings.fromJson(p[KEY]).let{it.copy(catEnabled=it.catEnabled+(cat.name to (on?:!it.catOn(cat))))};p[KEY]=result.toJson() }}finally{TravelSelection.mutex.unlock()}
        return result
    }
    /** 供无障碍服务/闹钟接收器等非协程上下文使用。 */
    fun loadBlocking(ctx: Context): AppSettings = runBlocking { load(ctx) }

    fun saveBlocking(ctx: Context, s: AppSettings) = runBlocking { save(ctx, s) }
}
