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
        val saved = AppLanguage.fromTag(p.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.tag))
        // Prefer our in-app choice. If the user never chose one, honour AppCompat's stored locale.
        val appCompatTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .substringBefore(',')
            .ifBlank { null }
        val fromAppCompat = AppLanguage.fromTag(appCompatTag)
        val language = when {
            p.contains(KEY_LANGUAGE) -> saved
            appCompatTag == null -> AppLanguage.ENGLISH
            else -> fromAppCompat
        }
        _language.value = language
        _streakDays.value = p.getInt(KEY_STREAK, 0)
        p.edit().putString(KEY_LANGUAGE, language.tag).apply()
        applyLocales(language)
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
        // commit so the choice survives even if the process is killed during locale apply
        prefs?.edit()?.putString(KEY_LANGUAGE, language.tag)?.commit()
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
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .substringBefore(',')
            .substringBefore('-')
        // Skip when already applied so setApplicationLocales does not recreate in a loop.
        if (current.equals(language.tag, ignoreCase = true)) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
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
