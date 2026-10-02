package com.kekitemkekifalta.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Sistema"),
    LIGHT("light", "Claro"),
    DARK("dark", "Escuro");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

data class Settings(
    val householdId: String,
    val notificationsEnabled: Boolean,
    /** Minutes after midnight, local time. */
    val notificationMinuteOfDay: Int,
    val themeMode: ThemeMode,
    val lastMarketId: String?,
)

/** Device-local preferences (not part of the synced data). */
interface SettingsStore {
    val settings: StateFlow<Settings>
    val householdId: String get() = settings.value.householdId
    fun setHouseholdId(id: String)
    fun setNotificationsEnabled(enabled: Boolean)
    fun setNotificationTime(minuteOfDay: Int)
    fun setThemeMode(mode: ThemeMode)
    fun setLastMarketId(id: String?)
    var lastNotifiedEpochDay: Long
    var askedNotificationPermission: Boolean
}

class PrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("kekitem_settings", Context.MODE_PRIVATE)

    init {
        if (prefs.getString(KEY_HOUSEHOLD, null) == null) {
            prefs.edit { putString(KEY_HOUSEHOLD, UUID.randomUUID().toString()) }
        }
    }

    private val state = MutableStateFlow(read())
    override val settings: StateFlow<Settings> = state.asStateFlow()

    private fun read() = Settings(
        householdId = prefs.getString(KEY_HOUSEHOLD, null) ?: UUID.randomUUID().toString(),
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFY, true),
        notificationMinuteOfDay = prefs.getInt(KEY_NOTIFY_TIME, 9 * 60),
        themeMode = ThemeMode.fromKey(prefs.getString(KEY_THEME, null)),
        lastMarketId = prefs.getString(KEY_LAST_MARKET, null),
    )

    private fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true) { block() }
        state.value = read()
    }

    override fun setHouseholdId(id: String) = update { putString(KEY_HOUSEHOLD, id) }
    override fun setNotificationsEnabled(enabled: Boolean) = update { putBoolean(KEY_NOTIFY, enabled) }
    override fun setNotificationTime(minuteOfDay: Int) = update { putInt(KEY_NOTIFY_TIME, minuteOfDay.coerceIn(0, 24 * 60 - 1)) }
    override fun setThemeMode(mode: ThemeMode) = update { putString(KEY_THEME, mode.key) }
    override fun setLastMarketId(id: String?) = update { putString(KEY_LAST_MARKET, id) }

    override var lastNotifiedEpochDay: Long
        get() = prefs.getLong(KEY_LAST_NOTIFIED, Long.MIN_VALUE)
        set(value) = prefs.edit(commit = true) { putLong(KEY_LAST_NOTIFIED, value) }

    override var askedNotificationPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED_PERMISSION, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED_PERMISSION, value) }

    private companion object {
        const val KEY_HOUSEHOLD = "household_id"
        const val KEY_NOTIFY = "notify_enabled"
        const val KEY_NOTIFY_TIME = "notify_minute_of_day"
        const val KEY_THEME = "theme_mode"
        const val KEY_LAST_MARKET = "last_market_id"
        const val KEY_LAST_NOTIFIED = "last_notified_epoch_day"
        const val KEY_ASKED_PERMISSION = "asked_notification_permission"
    }
}
