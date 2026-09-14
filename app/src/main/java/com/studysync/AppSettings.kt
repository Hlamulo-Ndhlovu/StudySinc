package com.studysync

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings {
    private var prefs: SharedPreferences? = null

    private val _useDarkTheme = MutableStateFlow(false)
    val useDarkTheme: StateFlow<Boolean> = _useDarkTheme.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.ENGLISH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _streakDays = MutableStateFlow(0)
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _useDarkTheme.value = p.getBoolean(KEY_DARK, false)
        _notificationsEnabled.value = p.getBoolean(KEY_NOTIFICATIONS, true)
        _language.value = AppLanguage.fromTag(p.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.tag))
        _streakDays.value = p.getInt(KEY_STREAK, 0)
        applyLocales(_language.value)
    }

    fun setDarkTheme(enabled: Boolean) {
        _useDarkTheme.value = enabled
        prefs?.edit()?.putBoolean(KEY_DARK, enabled)?.apply()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_NOTIFICATIONS, enabled)?.apply()
    }

    fun setLanguage(language: AppLanguage) {
        if (_language.value == language) return
        _language.value = language
        prefs?.edit()?.putString(KEY_LANGUAGE, language.tag)?.apply()
        applyLocales(language)
    }

    /** Call when the signed-in user opens the hub so streak XP stays current. */
    fun noteDailyActivity() {
        val p = prefs ?: return
        val today = LocalDate.now().toEpochDay()
        val last = p.getLong(KEY_LAST_ACTIVE_DAY, Long.MIN_VALUE)
        val current = p.getInt(KEY_STREAK, 0)
        val next = when {
            last == today -> current.coerceAtLeast(1)
            last == today - 1L -> current + 1
            else -> 1
        }
        p.edit()
            .putLong(KEY_LAST_ACTIVE_DAY, today)
            .putInt(KEY_STREAK, next)
            .apply()
        _streakDays.value = next
    }

    private fun applyLocales(language: AppLanguage) {
        val locales = LocaleListCompat.forLanguageTags(language.tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        private const val PREFS = "studysync_settings"
        private const val KEY_DARK = "dark_theme"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_STREAK = "streak_days"
        private const val KEY_LAST_ACTIVE_DAY = "last_active_epoch_day"
    }
}
